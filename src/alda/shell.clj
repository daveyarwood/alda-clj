(ns ^:no-doc alda.shell
  (:require [me.raynes.conch.low-level :as conch])
  (:import [java.io ByteArrayOutputStream PrintWriter Writer]))

(defn multi-writer-with-autoflush
  "Given a seq of Writers, returns a single Writer that writes the same output
   to all of them. Output is flushed automatically on every write.

   Adapted from: https://stackoverflow.com/a/7987606/2338327"
  [writers]
  ;; I had to comment some of these methods out because Clojure doesn't let you
  ;; do multiple overloads of the same arity. This isn't great, but I guess it's
  ;; alright because you can still accomplish whatever you need to do by using
  ;; the overloads I kept.
  (proxy [Writer] []
    ;; (append [^char c]
    ;;   (run! #(.append % c)))
    (append
      ([^CharSequence csq]
       (run! #(.append % csq)))
      ([^CharSequence csq start end]
       (run! #(.append % csq start end))))
    (close []
      (run! #(.close %) writers))
    (flush []
      (run! #(.flush %) writers))
    (nullWriter []
      (throw (ex-info "Method not supported" {:method "nullWriter"})))
    (write
      ([cbuf]
       (run! #(.write % cbuf) writers)
       (run! #(.flush %) writers))
      ([^chars cbuf offset length]
       (run! #(.write % cbuf offset length) writers)
       (run! #(.flush %) writers)))))
      ;; ([^char c]
      ;;  (run! #(.write % c) writers)))))
      ;; ([^String s]
      ;;  (run! #(.write % s) writers)))))
      ;; ([^String s offset length]
      ;;  (run! #(.write % s offset length) writers)))))

(defn sh
  "Starts a process via me.raynes.conch.low-level/proc.

   Shell output is streamed to stdout and stderr as it is produced
   (implementation adapted from boot.util/sh).

   Like clojure.java.shell/sh, waits for the process to exit and then returns a
   map containing:
     :exit  exit code      (int)
     :out   stdout output  (string)
     :err   stderr output  (string)"
  [cmd & args]
  (let [proc                (apply conch/proc cmd args)
        [out-copy err-copy] (repeatedly 2 #(ByteArrayOutputStream.))
        out-writer          (multi-writer-with-autoflush
                              [*out* (PrintWriter. out-copy)])
        err-writer          (multi-writer-with-autoflush
                              [*err* (PrintWriter. err-copy)])
        out-future          (future (conch/stream-to proc :out out-writer))
        err-future          (future (conch/stream-to proc :err err-writer))
        exit-code           (.waitFor (:process proc))]
    ;; Ensure that all of the output is written to `out-copy` and `err-copy`
    ;; before we return it as a string.
    @out-future
    @err-future
    {:out  (str out-copy)
     :err  (str err-copy)
     :exit exit-code}))

(ns alda.core
  (:require [alda.shell         :as sh]
            [clojure.string     :as str]
            [jsonista.core      :as json])
  (:import [java.io File]
           [java.nio.file Paths]))

(def ^:dynamic *alda-executable*
  "The path to the `alda` executable.

   The default value is \"alda\", which will depend on your PATH."
  "alda")

(defn- alda*
  "Like [[alda]], but doesn't throw an exception when the exit code is
   non-zero.

   Shell output is streamed to stdout and stderr as it is produced
   (implementation adapted from boot.util/sh).

   Returns a map containing:
     :exit  exit code      (int)
     :out   stdout output  (string)
     :err   stderr output  (string)"
  [& args]
  (apply sh/sh (cons *alda-executable* args)))

(defn alda
  "Invokes `alda` at the command line, using `args` as arguments.

   The return value is the string of stdout, if the command was successful, i.e.
   if the exit code was 0.

   If the exit code is non-zero, an ex-info is thrown, including context about
   the result and what command was run.

   Stdout and stderr output are printed as they are produced.

   Examples:

   ```clojure
   (alda \"version\")
   ;;=> \"alda 1.99.4\\n\"

   (alda \"parse\" \"-c\" \"bassoon: o3 c\")
   ;;=> \"{\\\"aliases\\\":{},\\\"current-parts\\\":[\\\"0xc0002509c0\\\"],...\"

   (alda \"\\\"make me a sandwich\\\"\")
   ;;=> Usage:
   ;;=>   alda [command]
   ;;=>
   ;;=> Available Commands:
   ;;=>   doctor      Run health checks to determine if Alda can run correctly
   ;;=>   export      Evaluate Alda source code and export to another format
   ;;=>   help        Help about any command
   ;;=>   instruments Display the list of available instruments
   ;;=>   parse       Display the result of parsing Alda source code
   ;;=>   play        Evaluate and play Alda source code
   ;;=>   ps          List background processes
   ;;=>   repl        Start an Alda REPL client/server
   ;;=>   shutdown    Shut down background processes
   ;;=>   stop        Stop playback
   ;;=>   telemetry   Enable or disable telemetry
   ;;=>   update      Update to the latest version of Alda
   ;;=>   version     Print Alda version information
   ;;=>
   ;;=> Flags:
   ;;=>   -v, --verbosity int   verbosity level (0-3) (default 1)
   ;;=>
   ;;=> Use \"alda [command] --help\" for more information about a command.
   ;;=>
   ;;=> ---
   ;;=>
   ;;=> Usage error:
   ;;=>
   ;;=>   unknown command \"make me a sandwich\" for \"alda\"
   ;;=> Execution error (ExceptionInfo) at alda.core/alda (core.clj:58).
   ;;=> Non-zero exit status.
   ```"
  [& args]
  (let [{:keys [exit out] :as result} (apply alda* args)]
    (when-not (zero? exit)
      (throw (ex-info "Non-zero exit status."
                      (assoc result
                             :command *alda-executable*
                             :args args))))
    out))

(defprotocol ^:no-doc Stringify
  (-str [this]))

;; When provided with a sequence of events, ->str intelligently injects spaces
;; and newlines between them based on the types of the events. We have to
;; declare ->str here because we need to define all the event types below before
;; we can define ->str.
(declare ->str)

(defprotocol ^:no-doc LispForm
  (-lisp-form [this]))

(defn ->lisp-form
  "Returns an S-expression representation of its argument, which must implement
   the LispForm protocol.

   Objects that implement the LispForm protocol have corresponding Lisp
   representations in alda-lisp."
  [object]
  (when-not (satisfies? LispForm object)
    (throw (ex-info "LispForm protocol not implemented."
                    {:object object, :object-type (type object)})))
  (-lisp-form object))

(defn read-alda-nrepl-port-file
  "If an .alda-nrepl-port file is present in the current directory, reads the
   file and returns the port number that the Alda REPL server is running on.

   Returns nil if the file doesn't exist, e.g. if you didn't start a REPL in
   the current directory by running `alda repl --server`.

   Throws an exception if an integer can't be parsed from the contents of the
   file. (This should never happen!)"
  []
  (let [port-file-path (str (Paths/get
                              (System/getProperty "user.dir")
                              (into-array [".alda-nrepl-port"])))]
    (when (.isFile (File. port-file-path))
      (Integer/parseInt (slurp port-file-path)))))

(def ^:dynamic *alda-nrepl-server-info* nil)

(defn connect!
  "Sets the host and port number used internally by alda-clj to send messages to
   the Alda REPL server.

   By default, alda-clj uses `alda play` on every call to `(play! ...)` to play
   each score in an isolated context. This might be sufficient if you're just
   experimenting or trying out the alda-clj library for the first time and you
   don't feel like starting an Alda REPL server.

   For a better experience when live-coding or composing interactively, start an
   Alda REPL server by running `alda repl --server`, and then use
   (`connect! ...)` to configure alda-clj to talk to the Alda REPL server.

   When called without arguments, `(connect!)` assumes that the REPL server is
   running on localhost and attempts to read the port from the
   `.alda-nrepl-port` file that Alda created.

   To specify a different host or port, you can include a map as an argument,
   e.g.:

   ```clojure
   (connect! {:port 12345})              ; localhost:12345
   (connect! {:host \"1.2.3.4\" 12345})  ; 1.2.3.4:12345
   ```

   To restore the default state where alda-clj uses `alda play` instead of
   talking to an Alda REPL server, use [[disconnect!]]."
  [& [{:keys [host port]
       :or {host "localhost"}}]]
  (let [port        (or port (read-alda-nrepl-port-file))
        server-info {:host host :port port}]
    (when-not port
      (throw (ex-info ":port not specified and no .alda-nrepl-port file found."
                      {:server-info server-info})))
    (alter-var-root #'*alda-nrepl-server-info* (constantly server-info))
    (println "Alda REPL server host/port set:" server-info)))

(defn disconnect!
  "Clears out the host and port number used internally by alda-clj to send
   messages to the Alda REPL server.

   This puts alda-clj back into its default state, where it uses `alda play` on
   every call to `(play! ...)` instead of sending messages to an Alda REPL
   server.

   See [[connect!]] for more information."
  []
  (alter-var-root #'*alda-nrepl-server-info* (constantly nil))
  (println "Alda REPL server host/port un-set."))

(defn require-connection!
  []
  (when-not *alda-nrepl-server-info*
    (throw (ex-info
             "Unspecified Alda REPL host/port. Use `connect!` first."
             {}))))

(defn send-nrepl-message!
  "Sends an nREPL message to the Alda REPL server defined in
   *alda-nrepl-server-info*.

   (See [[connect!]] and [[disconnect!]] for information about configuring
   alda-clj to talk to your Alda REPL server."
  [msg]
  (require-connection!)
  (let [{:keys [host port]} *alda-nrepl-server-info*]
    (-> (alda "repl"
              "--host" host
              "--port" (str port)
              "--message" (json/write-value-as-string msg))
        json/read-value)))

(defn score-text
  "Returns the string of Alda code that is \"loaded\" into the Alda REPL session.

   Throws an exception if you haven't connected to an Alda REPL server yet.
   (See [[connect!]] for more information.)"
  []
  (require-connection!)
  (-> (send-nrepl-message! {:op "score-text"})
      (get "text")
      str/trim))

(defn new-score!
  "Resets the Alda REPL server's state and initializes a new score.

   Throws an exception if you haven't connected to an Alda REPL server yet.
   (See [[connect!]] for more information.)"
  []
  (require-connection!)
  (send-nrepl-message! {:op "new-score"}))

(defn play!
  "Converts its arguments into a string of Alda code (via [[->str]]) and sends
   it to the Alda CLI to be parsed and played.

   Returns the string of code that was sent to `alda play`."
  [& xs]
  (let [code (->str xs)]
    (if *alda-nrepl-server-info*
      (send-nrepl-message! {:op "eval-and-play", :code code})
      (alda "play" "--code" code))
    code))

(defn stop!
  "Stops playback.

   When connected to an Alda REPL server, this is done by sending a \"stop\"
   message to the server.

   Otherwise, we stop playback globally by running `alda stop`."
  []
  (if *alda-nrepl-server-info*
    (send-nrepl-message! {:op "stop"})
    (alda "stop")))

(defrecord InstrumentCall [names nickname]
  Stringify
  (-str [{:keys [names nickname]}]
    (str (str/join \/ names)
         (when nickname
           (format " \"%s\"" nickname))
         \:)))

(defn- parse-instrument-call
  [s]
  (let [regex #"([a-zA-Z0-9._/-]+)(?:\s+['\"]([a-zA-Z0-9_/-]+)['\"])?:?"
        [_ names nickname] (re-matches regex s)]
    (when-not names
      (throw (ex-info "Failed to parse instrument call."
                      {:input s})))
    {:names    (str/split names #"/")
     :nickname nickname}))

(defn part
  "Sets the current instrument instance(s) based on `instrument-call`.

   `instrument-call` can either be a map containing `:names` and an optional
   `:nickname` (e.g. `{:names [\"piano\" \"trumpet\"] :nickname
   [\"trumpiano\"]}`) or a valid Alda instrument call string, e.g.
   `\"piano/trumpet 'trumpiano'\"`.

   For convenience, single quotes can be used around the nickname instead of
   double quotes.

   Examples:
   ```clojure
   (part \"piano\")
   ;;=> #alda.core.InstrumentCall{:names [\"piano\"],
   ;;=>                           :nickname nil}

   (part \"piano/trumpet\")
   ;;=> #alda.core.InstrumentCall{:names [\"piano\" \"trumpet\"],
   ;;=>                           :nickname nil}

   (part {:names [\"piano\" \"trumpet\"] :nickname \"trumpiano\"})
   ;;=> #alda.core.InstrumentCall{:names [\"piano\" \"trumpet\"],
   ;;=>                           :nickname \"trumpiano\"}

   (->str (part {:names [\"piano\" \"trumpet\"] :nickname \"trumpiano\"}))
   ;;=> \"piano/trumpet \\\"trumpiano\\\":\"

   (play!
     (part \"piano/trumpet 'trumpiano'\")
     (note (pitch :c)))
   ;;=> \"piano/trumpet \\\"trumpiano\\\":\\nc\"
   ```"
  [x]
  (let [instrument-call (cond
                          (map? x)
                          x

                          (string? x)
                          (parse-instrument-call x)

                          :else
                          (throw (ex-info "Invalid instrument call."
                                          {:instrument-call x})))]
    (map->InstrumentCall instrument-call)))

(defrecord LetterAndAccidentals [letter accidentals]
  Stringify
  (-str [{:keys [letter accidentals]}]
    (apply str
           (name letter)
           (map {:flat \- :sharp \+ :natural \_} accidentals)))

  LispForm
  (-lisp-form [{:keys [letter accidentals]}]
    (list* 'pitch letter accidentals)))

(defn pitch
  "Returns the pitch component of a note.

   Examples:

   ```clojure
   (pitch :c :sharp)
   ;;=> #alda.core.LetterAndAccidentals{:letter :c, :accidentals (:sharp)}

   (->str (pitch :c :sharp))
   ;;=> \"c+\"

   (->str (pitch :g))
   ;;=> \"g\"

   (play!
     (part \"piano\")
     (note (pitch :c)))
   ;;=> \"piano:\\nc\"
   ```"
  [letter & accidentals]
  (map->LetterAndAccidentals {:letter letter :accidentals accidentals}))

(defrecord MidiNoteNumber [note-number]
  LispForm
  (-lisp-form [{:keys [note-number]}]
    (list 'midi-note note-number)))

(defn midi-note
  "Returns the pitch component of a note, expressed as a MIDI note number.

   See also [[pitch]], which expresses pitch as a letter and accidentals.

   > [[->str]] cannot be used on a MIDI note number component directly because
   > there is no native Alda syntax for a MIDI note number; this feature is only
   > accessible via the `midi-note` alda-lisp function.
   >
   > In alda-clj, just like in alda-lisp, `midi-note` can be used instead of
   > `pitch` as the pitch component of a note. To stringify a note with a
   > `midi-note` pitch component, we fallback to emitting alda-lisp code.

   Examples:

   ```clojure
   (midi-note 42)
   ;;=> #alda.core.MidiNoteNumber{:note-number 42}

   (->lisp-form (midi-note 42))
   ;;=> (midi-note 42)

   (->str (midi-note 42))
   ;;=> Execution error (IllegalArgumentException) at alda.core/eval160$fn$G (core.clj:71).
   ;;=> No implementation of method: :-str of protocol: #'alda.core/Stringify found for class: alda.core.MidiNoteNumber

   (->str (note (midi-note 42) (note-length 8)))
   ;;=> \"(note (midi-note 42) (note-length 8))\"

   (->str (note (pitch :c) (note-length 8)))
   ;;=> \"c8\"
   ```"
  [note-number]
  (map->MidiNoteNumber {:note-number note-number}))

(defrecord Tie []
  Stringify
  (-str [_] "~"))

(defrecord Slur []
  Stringify
  (-str [_] "~"))

(defrecord Duration [components]
  Stringify
  (-str [{:keys [components]}]
    (str/join (->str (->Tie))
              (map ->str components)))

  LispForm
  (-lisp-form [{:keys [components]}]
    (list* 'duration (map ->lisp-form components))))

(defrecord NoteLength [number dots]
  Stringify
  (-str [{:keys [number dots]}]
    (apply str number (repeat dots \.)))

  LispForm
  (-lisp-form [{:keys [number dots]}]
    (list* 'note-length number (if (and dots (pos? dots))
                                 [{:dots dots}]
                                 []))))

(defn note-length
  "Returns a note length component, which is expressed as an integer (e.g. `4`
   is a quarter note) and, optionally, 1 or more dots (e.g. to make a dotted
   quarter note, double dotted half note, etc.).

   Examples:

   ```clojure
   (note-length 8)
   ;;=> #alda.core.NoteLength{:number 8, :dots 0}

   (->str (note-length 4 {:dots 2}))
   ;;=> \"4..\"

   (play!
     (part \"piano\")
     (note (pitch :c)
           (note-length 1 {:dots 1})))
   ;;=> \"piano:\\nc1.\"
   ```"
  [number & [{:keys [dots]}]]
  (map->NoteLength {:number number :dots (or dots 0)}))

(defrecord Milliseconds [number]
  Stringify
  (-str [{:keys [number]}]
    (str number "ms"))

  LispForm
  (-lisp-form [{:keys [number]}]
    (list 'ms number)))

(defn ms
  "Returns a millisecond note length component.

   Examples:

   ```clojure
   (ms 456)
   ;;=> #alda.core.Milliseconds{:number 456}

   (ms 456)
   ;;=> \"456ms\"

   (play!
     (part \"piano\")
     (note (pitch :c) (ms 1005)))
   ;;=> \"piano:\\nc1005ms\"
   ```"
  [number]
  (map->Milliseconds {:number number}))

(defn duration
  "Returns a duration component, which consists of multiple note length
   components \"tied\" together.

   Examples:

   ```clojure
   (duration (note-length 8 {:dots 1})
             (note-length 16)
             (ms 250))
   ;;=> #alda.core.Duration{
   ;;=>   :components (#alda.core.NoteLength{:number 8, :dots 1}
   ;;=>                #alda.core.NoteLength{:number 16, :dots 0}
   ;;=>                #alda.core.Milliseconds{:number 250})}

   (->str (duration (note-length 8 {:dots 1})
                    (note-length 16)
                    (ms 250)))
   ;;=> \"8.~16~250ms\"

   (play!
     (part \"piano\")
     (note (pitch :c)
           (duration (note-length 1)
                     (note-length 1)
                     (note-length 2 {:dots 1}))))
   ;;=> \"piano:\\nc1~1~2.\"
   ```"
  [& components]
  (map->Duration {:components components}))

(defrecord Note [pitch duration slurred?]
  Stringify
  (-str [{:keys [pitch duration slurred?] :as note}]
    (if (instance? MidiNoteNumber pitch)
      (->str (->lisp-form note))
      (str (->str pitch)
           (when duration (->str duration))
           (when slurred?
             (->str (->Slur))))))

  LispForm
  (-lisp-form [{:keys [pitch duration slurred?]}]
    (remove nil?
            (list 'note
                  (->lisp-form pitch)
                  (when duration (->lisp-form duration))
                  (when slurred? :slur)))))

(defn note
  "Causes every active instrument to play a note at its current offset for the
   specified duration.

   If no duration is specified, the note is played for the instrument's own
   internal duration, which will be the duration last specified on a note or
   rest in that instrument's part.

   A third argument, `slur?` may be optionally included. When truthy, this
   includes a final slur (`~`) to be appended to the code that is generated.
   This means the note will be sustained for the absolute fullest value, with
   minimal space between this note and the next. (By default, there is a small
   amount of space between notes.)

   Examples:

   ```clojure
   (note (pitch :d :flat))
   ;;=> #alda.core.Note{
   ;;=>   :pitch #alda.core.LetterAndAccidentals{
   ;;=>            :letter :d,
   ;;=>            :accidentals (:flat)},
   ;;=>   :duration nil,
   ;;=>   :slurred? nil}

   (->str (note (pitch :d :flat)))
   ;;=> \"d-\"

   (->str (note (pitch :f) (note-length 8)))
   ;;=> \"f8\"

   (->str (note (pitch :f)
                (duration (note-length 1) (note-length 8))
                :slur))
   ;;=> \"f1~8~\"

   (play!
     (part \"piano\")
     (note (pitch :g)))
   ;;=> \"piano:\\ng\"
   ```"
  [pitch & [duration slur?]]
  (map->Note {:pitch    pitch
              :duration duration
              :slurred? slur?}))

(defrecord Rest [duration]
  Stringify
  (-str [{:keys [duration]}]
    (str \r (when duration (->str duration)))))

(defn pause
  "Causes every active instrument to rest (not play) for the specified duration.

   If no duration is specified, each instrument will rest for its own internal
   duration, which will be the duration last specified on a note or rest in that
   instrument's part.

   Examples:

   ```clojure
   (pause (note-length 2))
   ;;=> #alda.core.Rest{:duration #alda.core.NoteLength{:number 2, :dots 0}}

   (->str (pause (note-length 2)))
   ;;=> \"r2\"

   (play!
     (part \"piano\")
     (note (pitch :c) (note-length 8))
     (pause (note-length 8))
     (note (pitch :c) (note-length 8))
     (pause (note-length 8))
     (note (pitch :c) (note-length 8))
     (pause (note-length 8)))
   ;;=> \"piano:\\nc8 r8 c8 r8 c8 r8\"
   ```"
  [& [duration]]
  (map->Rest {:duration duration}))

(defrecord ChordNoteSeparator []
  Stringify
  (-str [_] "/"))

(defrecord Chord [events]
  Stringify
  (-str [{:keys [events]}]
    (->str (cons (first events)
                 (mapcat #(if (or (instance? Note %) (instance? Rest %))
                            [(->ChordNoteSeparator) %]
                            [%])
                         (rest events))))))

(defn chord
  "Causes every active instrument to play each note in the chord simultaneously
   at the instrument's current offset.

   Events may include notes, rests, and attribute change (e.g. octave change)
   events.

   Examples:

   ```clojure
   (chord (note (pitch :c))
          (note (pitch :e))
          (note (pitch :g)))
   ;;=> #alda.core.Chord{
   ;;=>   :events (#alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :c,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :e,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :g,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil})}

   (->str (chord (note (pitch :c))
                 (note (pitch :e))
                 (note (pitch :g))))
   ;;=> \"c / e / g\"

   (play!
     (part \"piano\")
     (chord (note (pitch :e))
            (note (pitch :g))
            (octave :up)
            (note (pitch :c))))
   ;;=> \"piano:\\ne / g > / c\"
   ```"
  [& events]
  (map->Chord {:events events}))

(defrecord OctaveSet [octave-number]
  Stringify
  (-str [{:keys [octave-number]}]
    (str \o octave-number)))

(defrecord OctaveShift [direction]
  Stringify
  (-str [{:keys [direction]}]
    (condp = direction
      :up   ">"
      :down "<"
      'up   ">"
      'down "<")))

(defn octave
  "Sets the current octave, which is used to calculate the pitch of notes.

   `value` can be an octave number, `:up` or `:down`.

   Examples:

   ```clojure
   (octave 3)
   ;;=> #alda.core.OctaveSet{:octave-number 3}


   (->str (octave 3))
   ;;=> \"o3\"

   (octave :down)
   ;;=> #alda.core.OctaveShift{:direction :down}

   (->str (octave :down))
   ;;=> \"<\"

   (play!
     (part \"piano\")
     (octave 0)
     (note (pitch :c) (note-length 8))
     (octave :up)
     (note (pitch :c))
     (octave :up)
     (note (pitch :c))
     (octave :up)
     (note (pitch :c))
     (octave :up)
     (note (pitch :c))
     (octave :up)
     (note (pitch :c))
     (octave :up)
     (note (pitch :c))
     (octave :up)
     (note (pitch :c)))
   ;;=> \"piano:\\no0 c8 > c > c > c > c > c > c > c\"
   ```"
  [value]
  (cond
    (number? value)
    (map->OctaveSet {:octave-number value})

    (#{:up :down 'up 'down} value)
    (map->OctaveShift {:direction value})

    :else
    (throw (ex-info "Invalid octave value." {:value value}))))

(defrecord Barline []
  Stringify
  (-str [_] "|"))

(defn barline
  "Barlines, at least currently, do nothing beyond visually separating other
   events.

   Examples:

   ```clojure
   (barline)
   ;;=> #alda.core.Barline{}

   (->str (barline))
   ;;=> \"|\"

   (play!
     (part \"piano\")
     (note (pitch :c) (note-length 4))
     (note (pitch :d) (note-length 8))
     (note (pitch :e))
     (note (pitch :f))
     (note (pitch :g))
     (note (pitch :a))
     (note (pitch :b))
     (octave :up)
     (barline)
     (note (pitch :c)))
   ;;=> \"piano:\\nc4 d8 e f g a b > | c\"
   ```"
  []
  (->Barline))

(defrecord Marker [name]
  Stringify
  (-str [{:keys [name]}]
    (str \% name)))

(defn marker
  "Places a marker at the current absolute offset.

   Examples:

   ```clojure
   (marker \"verse-1\")
   ;;=> #alda.core.Marker{:name \"verse-1\"}

   (->str (marker \"verse-1\"))
   ;;=> \"%verse-1\"

   (println
     (play!
       (part \"piano\")
       \"o4 c8 d e f\"
       (marker \"here\")
       \"g2\"

       (part \"electric-bass\")
       (at-marker \"here\")
       \"o2 g2\"

       (part \"trombone\")
       (at-marker \"here\")
       \"o3 b2\"))
   ;;=> piano:
   ;;=> o4 c8 d e f %here g2
   ;;=> electric-bass:
   ;;=> @here o2 g2
   ;;=> trombone:
   ;;=> @here o3 b2
   ```"
  [name]
  (map->Marker {:name name}))

(defrecord AtMarker [name]
  Stringify
  (-str [{:keys [name]}]
    (str \@ name)))

(defn at-marker
  "Sets the active instruments' current offset to the offset of the marker with
   the provided name.

   Examples:

   ```clojure
   (at-marker \"verse-1\")
   ;;=> #alda.core.AtMarker{:name \"verse-1\"}

   (->str (at-marker \"verse-1\"))
   ;;=> \"@verse-1\"

   (println
     (play!
       (part \"piano\")
       \"o4 c8 d e f\"
       (marker \"here\")
       \"g2\"

       (part \"electric-bass\")
       (at-marker \"here\")
       \"o2 g2\"

       (part \"trombone\")
       (at-marker \"here\")
       \"o3 b2\"))
   ;;=> piano:
   ;;=> o4 c8 d e f %here g2
   ;;=> electric-bass:
   ;;=> @here o2 g2
   ;;=> trombone:
   ;;=> @here o3 b2
   ```"
  [name]
  (map->AtMarker {:name name}))

(defrecord Voice [number]
  Stringify
  (-str [{:keys [number]}]
    (str \V number \:)))

(defn voice
  "Begins a voice identified by `number`.

   Until the voice group ends, all voices are played simultaneously.

   Examples:

   ```clojure

   (voice 2)
   ;;=> #alda.core.Voice{:number 2}

   (->str (voice 2))
   ;;=> \"V2:\"

   (play!
     (part \"clarinet\")

     (voice 1)
     (octave 4)
     (note (pitch :c) (note-length 8))
     (note (pitch :d))
     (note (pitch :e))
     (note (pitch :f) (note-length 2))

     (voice 2)
     (octave 4)
     (note (pitch :e) (note-length 8))
     (note (pitch :f))
     (note (pitch :g))
     (note (pitch :a) (note-length 2))

     (voice 3)
     (octave 4)
     (note (pitch :g) (note-length 8))
     (note (pitch :a))
     (note (pitch :b))
     (octave :up)
     (note (pitch :c) (note-length 2))))
   ;;=> \"clarinet:\\nV1: o4 c8 d e f2 V2: o4 e8 f g a2 V3: o4 g8 a b > c2\"
   ```"
  [number]
  (map->Voice {:number number}))

(defrecord EndVoices []
  Stringify
  (-str [_] "V0:"))

(defn end-voices
  "Ends the current voice group.

   Examples:

   ```clojure
   (end-voices)
   ;;=> #alda.core.EndVoices{}

   (->str (end-voices))
   ;;=> \"V0:\"

   (play!
     (part \"piano\")

     (voice 1)
     (octave 4)
     (note (pitch :c) (note-length 8))
     (note (pitch :d))
     (note (pitch :e))
     (note (pitch :f))
     (note (pitch :g))
     (note (pitch :a))

     (voice 2)
     (octave 3)
     (note (pitch :g) (note-length 4 {:dots 1}))
     (note (pitch :d) (note-length 4 {:dots 1}))

     (end-voices)

     (octave 3)
     (chord
       (note (pitch :f) (note-length 1))
       (octave :up)
       (note (pitch :c))
       (note (pitch :a))))
   ;;=> \"piano:\\nV1: o4 c8 d e f g a V2: o3 g4. d4. V0: o3 f1 > / c / a\"
   ```"
  []
  (->EndVoices))

(defrecord Cram [duration events]
  Stringify
  (-str [{:keys [duration events]}]
    (->str ["{" events
            (str "}"
                 (if duration (->str duration) ""))])))

(defn cram
  "A cram expression time-scales the events it contains based on the ratio of
   the \"inner duration\" of the events to the \"outer duration\" of each
   current instrument.

   Examples:

   ```clojure
   (cram (note-length 2)
     (note (pitch :e))
     (note (pitch :f))
     (note (pitch :e))
     (note (pitch :f))
     (note (pitch :e)))
   ;;=> #alda.core.Cram{
   ;;=>   :duration #alda.core.NoteLength{:number 2, :dots 0},
   ;;=>   :events (#alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :e,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :f,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :e,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :f,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :e,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil})}

   (->str (cram (note-length 2)
            (note (pitch :e))
            (note (pitch :f))
            (note (pitch :e))
            (note (pitch :f))
            (note (pitch :e))))
   ;;=> \"{ e f e f e }2\"

   (play!
     (part \"piano\")
     (octave 4)
     (cram (note-length 1)
       (note (pitch :c))
       (note (pitch :d))
       (cram nil
         (note (pitch :e))
         (note (pitch :f))
         (note (pitch :g)))
       (note (pitch :a))
       (note (pitch :b)))
     (octave :up)
     (note (pitch :c)))
   ;;=> \"piano:\\no4 { c d { e f g } a b }1 > c\"
   ```"
  [duration & events]
  (map->Cram {:duration duration :events events}))

(defrecord Equals []
  Stringify
  (-str [_] "="))

(defrecord SetVariable [name events]
  Stringify
  (-str [{:keys [name events]}]
    (->str [name (->Equals) events])))

(defn set-variable
  "Defines any number of events as a variable so that they can be referenced by
   name.

   Examples:

   ```clojure
   (set-variable \"riffA\"
     (note (pitch :d))
     (note (pitch :f))
     (note (pitch :a)))
   ;;=> #alda.core.SetVariable{
   ;;=>   :name \"riffA\",
   ;;=>   :events (#alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :d,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :f,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil}
   ;;=>            #alda.core.Note{
   ;;=>              :pitch #alda.core.LetterAndAccidentals{
   ;;=>                       :letter :a,
   ;;=>                       :accidentals nil},
   ;;=>              :duration nil,
   ;;=>              :slurred? nil})}

   (->str (set-variable \"riffA\"
            (note (pitch :d))
            (note (pitch :f))
            (note (pitch :a))))
   ;;=> \"riffA = d f a\"

   (play!
     (set-variable \"riffA\"
       (note (pitch :d))
       (note (pitch :f))
       (note (pitch :a)))

     (part \"piano\")
     (get-variable \"riffA\"))
   ;;=> \"riffA = d f a\\npiano:\\nriffA\"
   ```"
  [var-name & events]
  (map->SetVariable {:name var-name :events events}))

(defrecord GetVariable [name]
  Stringify
  (-str [{:keys [name]}]
    name))

(defn get-variable
  "Returns any number of events previously defined as a variable.

   Examples:

   ```clojure
   (get-variable \"riffA\")
   ;;=> #alda.core.GetVariable{:name \"riffA\"}

   (->str (get-variable \"riffA\"))
   ;;=> \"riffA\"

   (play!
     (set-variable \"riffA\"
       (note (pitch :d))
       (note (pitch :f))
       (note (pitch :a)))

     (part \"piano\")
     (get-variable \"riffA\"))
   ;;=> \"riffA = d f a\\npiano:\\nriffA\"
   ```"
  [var-name]
  (map->GetVariable {:name var-name}))

(defn- spaced
  [xs]
  (->> (for [[e1 e2] (partition 2 1 (concat xs [:end]))]
         (cond
           (= :end e2)                   nil
           (instance? InstrumentCall e1) \newline
           (instance? InstrumentCall e2) \newline
           (instance? SetVariable e1)    \newline
           (instance? SetVariable e2)    \newline
           :else                         \space))
       (interleave (map ->str xs))
       (apply str)))

(defn ->str
  "Converts a value into a string of Alda code.

   This function is used under the hood by [[play!]] to convert its arguments
   into a string of code to send to the Alda CLI."
  [x]
  (cond
    (string? x)     x
    (sequential? x) (if (symbol? (first x))
                      (pr-str x)
                      (spaced x))
    :else           (-str x)))

;; FIXME: There is an issue with quoting, where the context about which forms
;; are quoted and which aren't gets lost in translation.
;;
;; For example, this works:
;;   '(key-sig! '(a major))    ; translates into (key-sig! (quote (a major)))
;;
;; But this does not:
;;   (key-sig! '(a major))     ; translates into (key-sig! (a major))
;;
;; alda-lisp doesn't like the second one because `a` cannot be resolved.
;;
;; I think the point where we are losing information is in the implementation of
;; Stringify for Sexp, where we call `(pr-str form)`.
;;
;; Potential solution: do some code walking, figure out which arguments appear
;; to be quoted (if that's possible?) and wrap them in `(quote ...)`, before
;; calling `(pr-str ...)`
(defrecord Sexp [form]
  Stringify
  (-str [{:keys [form]}] (pr-str form)))

(defmacro ^:private lisp-builtin
  [sym]
  `(defn ~sym
     ~(format "Emits inline Lisp code `(%s ...)`" sym)
     [& ~'args]
     (map->Sexp {:form (list* '~sym ~'args)})))

(defmacro ^:private lisp-builtins
  [& syms]
  (cons 'do
        (for [sym syms]
          `(lisp-builtin ~sym))))

(defmacro ^:private lisp-builtin-attributes
  [& syms]
  (cons 'do
        (for [sym syms]
          `(lisp-builtins ~sym ~(symbol (str sym \!))))))

(lisp-builtin-attributes
  tempo metric-modulation quant quantize quantization vol volume track-vol
  track-volume pan panning transpose transposition tuning-constant
  reference-pitch)

(lisp-builtins
  set-duration set-note-length)

(defn- lisp-quoting-workaround
  "Because of quoting issues, I wasn't able to define attributes like `octave`
   and `key-signature` (which take quoted arguments) via
   `lisp-builtin-attributes`.

   I'm circumventing the implementation of Stringify for Sexp here, which I'm
   not very happy about, but hey, it works.

   TODO: Adjust this to accept multiple arguments, if we ever need that."
  [attr-name x]
  (format "(%s %s)"
          attr-name
          (if (or (coll? x) (symbol? x))
            (str "'" (pr-str x))
            (pr-str x))))

;; NOTE: `octave` is already defined separately above, because there is a
;; dedicated syntax for (local) octave attribute changes, i.e, o3 or < or >.
;;
;; We need to do this hack for (global) `octave!` changes though.
(defn octave!
  "Emits inline Lisp code `(octave! ...)`

   Example usage:

   ```clojure
   (octave! 5)
   (octave! :up)
   (octave! :down)
   ```"
  [x]
  (lisp-quoting-workaround
    "octave!"
    ;; Allow both 'up / 'down and :up / :down, even though Alda 2 only accepts
    ;; 'up / 'down.
    (get {:up 'up, :down 'down} x x)))

(defn key-signature
  "Emits inline Lisp code `(key-signature ...)`

   You can specify the key signature in a few ways:

   ```clojure
   (key-signature \"f+ c+ g+\")
   (key-signature '(a major))
   (key-signature '(f (sharp) c (sharp) g (sharp)))
   ```"
  [x]
  (lisp-quoting-workaround "key-signature" x))

(defn key-signature!
  "Emits inline Lisp code `(key-signature! ...)`

   You can specify the key signature in a few ways:

   ```clojure
   (key-signature! \"f+ c+ g+\")
   (key-signature! '(a major))
   (key-signature! '(f (sharp) c (sharp) g (sharp)))
   ```"
  [x]
  (lisp-quoting-workaround "key-signature!" x))

(defn key-sig
  "Emits inline Lisp code `(key-sig ...)`

   You can specify the key signature in a few ways:

   ```clojure
   (key-sig \"f+ c+ g+\")
   (key-sig '(a major))
   (key-sig '(f (sharp) c (sharp) g (sharp)))
   ```"
  [x]
  (lisp-quoting-workaround "key-sig" x))

(defn key-sig!
  "Emits inline Lisp code `(key-sig! ...)`

   You can specify the key sig!nature in a few ways:

   ```clojure
   (key-sig! \"f+ c+ g+\")
   (key-sig! '(a major))
   (key-sig! '(f (sharp) c (sharp) g (sharp)))
   ```"
  [x]
  (lisp-quoting-workaround "key-sig!" x))

(defn- event-duration-map->Duration
  [{:keys [components]}]
  (map->Duration {:components (map (fn [{:keys [denominator dots]}]
                                     (note-length denominator dots))
                                   components)}))

(defmulti event-map->record (fn [event-map]
                              (select-keys event-map [:type
                                                      :attribute])))

(defmethod event-map->record :default
  [event-map]
  (throw (ex-info "Unknown event type." {:event-map event-map})))

(defmethod event-map->record {:type "part-declaration"}
  [{:keys [value]}]
  (map->InstrumentCall value))

(defmethod event-map->record {:type "note"}
  [{{:keys [duration pitch slurred?]} :value}]
  (map->Note {:pitch    (map->LetterAndAccidentals
                          (-> pitch
                              (update :letter str/lower-case)
                              (update :accidentals #(map keyword %))))
              :duration (event-duration-map->Duration duration)
              :slurred? slurred?}))

(defmethod event-map->record {:type "rest"}
  [{{:keys [duration]} :value}]
  (map->Rest {:duration (event-duration-map->Duration duration)}))

(defmethod event-map->record {:type "chord"}
  [{{:keys [events]} :value}]
  (map->Chord {:events (map event-map->record events)}))

(defmethod event-map->record {:type "cram"}
  [{:keys [value]}]
  (map->Cram value))

(defmethod event-map->record {:type "barline"}
  [_]
  (->Barline))

(defmethod event-map->record {:type "marker"}
  [{:keys [value]}]
  (map->Marker value))

(defmethod event-map->record {:type "at-marker"}
  [{:keys [value]}]
  (map->AtMarker value))

(defmethod event-map->record {:type "voice-marker"}
  [{:keys [value]}]
  (map->Voice value))

(defmethod event-map->record {:type "voice-group-end-marker"}
  [_]
  (->EndVoices))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "octave"}
  [{:keys [value]}]
  (cond
    (number? value)
    (map->OctaveSet {:octave-number value})

    (#{"up" "down"} value)
    (map->OctaveShift {:direction (keyword value)})

    :else
    (throw (ex-info "Invalid octave value." {:value value}))))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "tempo"}
  [{:keys [value]}]
  (tempo value))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "volume"}
  [{:keys [value]}]
  (volume value))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "track-volume"}
  [{:keys [value]}]
  (track-volume value))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "panning"}
  [{:keys [value]}]
  (panning value))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "quantization"}
  [{:keys [value]}]
  (quantization value))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "key-signature"}
  [{:keys [value]}]
  (key-signature value))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "transposition"}
  [{:keys [value]}]
  (transposition value))

(defmethod event-map->record {:type "attribute-update"
                              :attribute "reference-pitch"}
  [{:keys [value]}]
  (reference-pitch value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "octave"}
  [{:keys [value]}]
  (octave! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "tempo"}
  [{:keys [value]}]
  (tempo! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "volume"}
  [{:keys [value]}]
  (volume! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "track-volume"}
  [{:keys [value]}]
  (track-volume! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "panning"}
  [{:keys [value]}]
  (panning! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "quantization"}
  [{:keys [value]}]
  (quantization! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "key-signature"}
  [{:keys [value]}]
  (key-signature! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "transposition"}
  [{:keys [value]}]
  (transposition! value))

(defmethod event-map->record {:type "global-attribute-update"
                              :attribute "reference-pitch"}
  [{:keys [value]}]
  (reference-pitch! value))

(defn parse-events
  "Converts its arguments into a string of Alda code (via [[->str]]) and sends
   it to the Alda CLI to be parsed into events JSON.

   Returns a seq of deserialized records."
  [& xs]
  (map event-map->record
       (json/read-value
         (alda "parse" "--output" "events" "--code" (->str xs))
         json/keyword-keys-object-mapper)))

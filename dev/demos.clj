(ns demos
  (:require [alda.core :refer :all]))

;;; basic usage

(comment
  ;; shells out to the alda CLI
  (alda "version")
  (alda "status")

  ;; Forget about current tempo, octave, instruments, etc.
  ;; Start from a clean slate.
  (clear-history!)

  ;; stop playback
  (stop!)

  ;; shorthand for "alda play -c 'piano: o4 c1 / e- / g'"
  (play!
    (part "piano")
    (octave 4)
    (chord (note (pitch :c) (note-length 1))
           (note (pitch :e :flat))
           (note (pitch :g))))

  ;; working with sequences of musical events (notes, rests, etc.)
  (play!
    (part "piano")
    (set-note-length 16)
    (interpose (pause)
               (for [letter [:c :d :e :f :g]]
                 (note (pitch letter)))))

  ;; generate a sequence of random notes
  (play!
    (part "piano")
    (for [n (repeatedly 8 #(+ 100 (rand-int 800)))]
      (note (pitch (rand-nth [:c :e :g :a :b]))
            (ms n)))))

















;;; charge!

(comment
  (clear-history!)

  (def riff "c8 < g a b >")

  (play!
    (part "organ")
    (quant 70) (octave 3)

    (for [[semitones bpm]
          (->> [0 70]
               (iterate (fn [[s b]] [(inc s) (+ b 20)]))
               (take 25))]
      [(transpose semitones)
       (tempo bpm)
       riff])

    (note (pitch :c) (ms 5000))))






















;;; entropy

(comment
  (clear-history!)
  (stop!)

  (def REST-RATE 0.15)
  (def MS-LOWER 30)
  (def MS-UPPER 3000)
  (def MAX-OCTAVE 8)

  (defn random-note
    "Returns a random note in a random octave with a random duration in
     milliseconds.

     May randomly return a rest with a random duration in milliseconds, instead."
    []
    (let [ms (ms (rand-nth (range MS-LOWER MS-UPPER)))]
      (if (< (rand) REST-RATE)
        (pause (duration ms))
        (let [o (rand-int (inc MAX-OCTAVE))
              n [(keyword (str (rand-nth "abcdefg")))
                 (rand-nth [:sharp :flat :natural])]]
          [(octave o)
           (note (apply pitch n) (duration ms))]))))

  (play!
    (part "midi-electric-piano-1")
    (panning 25)
    (repeatedly 50 random-note)

    (part "midi-timpani")
    (panning 50)
    (repeatedly 50 random-note)

    (part "midi-celesta")
    (panning 75)
    (repeatedly 50 random-note)))

























;;; algorithmic bongos

(comment
  (defn random-bongos
    [ticks vol-min vol-max]
    [(octave 4)
     (set-note-length 16) ; 16th notes
     (let [notes     (-> #{[:c] [:c :sharp] [:d] [:d :sharp] [:e]}
                         (->> (map #(note (apply pitch %))))
                         (conj (pause)))
           rand-note #(rand-nth notes)
           rand-vol  #(vol (+ vol-min (rand-int (- vol-max vol-min))))
           tick      #(vector (rand-vol) (rand-note))]
       (take ticks (repeatedly tick)))])

  (play!
    (part "midi-percussion")

    ;; bongos
    (voice 1)
    (random-bongos (* 8 4 4) 25 75)

    ;; hi-hat to keep time
    (voice 2)
    (octave 2)
    (vol 50)
    (set-note-length 4)
    (repeat (* 8 4) (note (pitch :g :sharp)))

    (end-voices)

    ;; ding
    (vol 50) "o5 a1"))


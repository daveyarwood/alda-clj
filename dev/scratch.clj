(ns scratch
  (:require [alda.core :refer :all] :reload-all))

(comment
  (stop!)
  (connect!)
  (disconnect!)
  (println (score-text))
  (new-score!)
  (alda "doctor")
  (alda "--help")
  (play! (part "piano:"))
  (play! (part "bassoon/trumpet \"bars\""))
  (play! (part {:names ["violin" "viola"] :nickname "strings"}))
  (play! (repeat 8 "o4 g8 r"))
  (part "bassoon/trumpet 'bars'")
  (part {:names ["violin" "viola"] :nickname "strings"})
  (play! (note (pitch :c :sharp :sharp :flat) (note-length 8)))
  (note (pitch :c :sharp :sharp :flat) (note-length 8))

  (play!
    (part "piano")
    (octave 2)
    (note (pitch :c) (note-length 2))
    (part "accordion")
    (octave 3)
    (note (pitch :c) (note-length 2))
    (octave! :up)
    (part "piano/accordion")
    (note (pitch :c)))

  (play!
    (part "piano")
    (octave 4)
    (chord (note (pitch :c))
           (note (pitch :e :flat) nil :slur)
           (note (pitch :g) (note-length 2) :slur))
    (octave :up)
    (chord (note (pitch :c))
           (note (pitch :e :flat) nil :slur)
           (note (pitch :g) (note-length 1) :slur))
    (octave 'up)
    (chord (note (pitch :c))
           (note (pitch :e :flat) nil :slur)
           (note (pitch :g) (note-length 1) :slur)))

  (play!
    (part "piano")
    (interpose (pause (duration (note-length 16)))
               (for [letter [:c :d :e :f :g]]
                 (note (pitch letter) (note-length 16)))))

  (play!
    (part "guitar")
    (octave 4)
    (voice 1)
    (for [letter [:c :d :e :f]]
      (note (pitch letter) (note-length 8)))
    (voice 2)
    (for [letter [:e :f :g :a]]
      (note (pitch letter) (note-length 8))))

  (play!
    (part "trombone")
    (octave 3)
    (for [letter [:c :d]]
      (note (pitch letter) (note-length 8)))
    (cram (note-length 4)
      (for [letter [:c :d :e]]
        (note (pitch letter))))
    (note (pitch :f) (note-length 2 {:dots 1})))

  (play!
    (part "piano")
    (set-variable "foo"
                  (note (pitch :c))
                  (note (pitch :d))
                  (note (pitch :c)))
    (get-variable "foo")
    (cram (note-length 4)
      (for [letter [:c :d :e]]
        (note (pitch letter))))
    (note (pitch :f) (note-length 2 {:dots 1})))

  (play!
    (part "piano")
    (for [tempo (repeatedly 4 #(+ 60 (rand-int 200)))]
      [(list 'tempo tempo)
       (for [letter [:c :d :e :f :g]]
         (note (pitch letter) (note-length 8)))]))

  (play!
    '(tempo! 150)
    (part "piano")
    '(pan 0)
    (for [letter [:c :d :e :f :g :c :d :e :f :g]]
      (note (pitch letter) (note-length 8)))
    "\n"
    '(pan 100)
    (for [letter [:c :d :e :f :g :c :d :e :f :g]]
      (note (pitch letter) (note-length 8))))

  (play!
    '(tempo! 250)
    (part "piano")
    (for [pan-value (range 101)]
      [(panning pan-value)
       (note (pitch (rand-nth [:c :d :e :f :g :a :b]))
             (note-length 16))
       "\n"]))

  (play!
    (part "piano")
    (for [n (repeatedly 8 #(+ 100 (rand-int 1500)))]
      (note (pitch :c) (ms n))))

  (defn random-note
    []
    (let [letter     (rand-nth [:c :d :e :f :g :a :b :c])
          accidental (rand-nth [:sharp :flat nil])]
      (note (if accidental
              (pitch letter accidental)
              (pitch letter)))))

  (defn random-chord
    []
    (apply chord (repeatedly (+ 2 (rand-int 4)) random-note)))

  (->str (random-chord))

  (play!
    (part "piano")
    (set-note-length 1)
    (random-chord)
    (random-chord)
    (random-chord))

  (play!
    '(key-sig! '(a major))
    (part "piano")
    (note (pitch :c) (note-length 8))
    (note (pitch :d))
    (note (pitch :e))
    (note (pitch :f))
    (note (pitch :g))))



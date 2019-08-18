(ns alda.core
  (:require [clojure.java.shell :as sh]
            [clojure.string     :as str]))

(def ^:dynamic *alda-executable*
  "The path to the `alda` executable.

   The default value is \"alda\", which will depend on your PATH."
  "alda")

(defn alda
  "Invokes `alda` at the command line, using `args` as arguments.

   The return value is the string of STDOUT, if the command was successful, i.e.
   if the exit code was 0.

   If the exit code is non-zero, an ex-info is thrown, including context about
   the result and what command was run.

   Examples:

   ```clojure
   (alda \"version\")
   ;;=> \"Client version: 1.2.0\\nServer version: [27713] 1.2.0\\n\"

   (alda \"parse\" \"-c\" \"bassoon: o3 c\")
   ;;=> \"{\\\"chord-mode\\\":false,\\\"current-instruments\\\":[\\\"bassoon-ZXXDZ\\\"],...}\\n\"

   (alda \"\\\"make me a sandwich\\\"\")
   ;;=> ExceptionInfo Non-zero exit status.  clojure.core/ex-info (core.clj:4739)
   ;;=> #error {
   ;;=>  :cause \"Non-zero exit status.\"
   ;;=>  :data {:exit 3,
   ;;=>         :out \"Expected a command, got \\\"make me a sandwich\\\"\\n\\nFor usage instructions, see --help.\\n\",
   ;;=>         :err \"\",
   ;;=>         :command (\"alda\" \"\\\"make me a sandwich\\\"\")}
   ;;=>  :via
   ;;=>  [{:type clojure.lang.ExceptionInfo
   ;;=>    :message \"Non-zero exit status.\"
   ;;=>    :data {:exit 3, ...}
   ;;=>    :at [clojure.core$ex_info invokeStatic \"core.clj\" 4739]}]
   ;;=>  :trace
   ;;=>  [[clojure.core$ex_info invokeStatic \"core.clj\" 4739]
   ;;=>   ...]}
   ```"
  [& args]
  (let [command (cons *alda-executable* args)
        {:keys [exit out] :as result} (apply sh/sh command)]
    (if (zero? exit)
      out
      (throw (ex-info "Non-zero exit status."
                      (assoc result :command command))))))

;; Relevant to Alda 1.x. This will work differently in Alda 2.x.
(def ^:dynamic *alda-history*
  "A string representing the score so far. This is used as the value of the
   `--history` option for the `alda play` command when calling [[play!]].

   This provides Alda with context about the score, including which instrument
   is active, its current octave, current default note length, etc.

   Each time [[play!]] is successful, the string of code that was played is
   appended to [[*alda-history*]]."
  "")

(defn clear-history!
  "Resets `*alda-history*` to `\"\"`."
  []
  (alter-var-root #'*alda-history* (constantly "")))

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

(defn play!
  "Converts its arguments into a string of Alda code (via [[->str]]) and sends
   it to the Alda CLI to be parsed and played.

   [[*alda-history*]] is sent along for context.

   Returns the string of code that was sent to `alda play`."
  [& xs]
  (let [code (->str xs)]
    (alda "play" "--history" *alda-history* "--code" code)
    (alter-var-root #'*alda-history* str code \newline)
    code))

(defn stop!
  "Runs `alda stop`, stopping playback."
  []
  (alda "stop"))

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
    (case direction
      :up   ">"
      :down "<")))

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

    (#{:up :down} value)
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
  track-volume pan panning key-sig key-signature transpose transposition
  tuning-constant reference-pitch)

(lisp-builtins
  set-duration set-note-length octave!)


(ns alda.core
  (:require [clojure.java.shell :as sh]
            [clojure.string     :as str]))

(def ^:dynamic *alda-executable* "alda")

(defn alda
  [& args]
  (let [command (cons *alda-executable* args)
        {:keys [exit out] :as result} (apply sh/sh command)]
    (if (zero? exit)
      out
      (throw (ex-info "Non-zero exit status."
                      (assoc result :command command))))))

;; Alda 1.x: We retain context when playing one score snippet after another by
;; tracking everything we send in a "history" string and sending it along with
;; every request.
(def ^:dynamic *alda-history* "")

(defn clear-history!
  []
  (alter-var-root #'*alda-history* (constantly "")))

(defprotocol Stringify
  (-str [this]))

;; When provided with a sequence of events, ->str intelligently injects spaces
;; and newlines between them based on the types of the events. We have to
;; declare ->str here because we need to define all the event types below before
;; we can define ->str.
(declare ->str)

(defn play!
  [& xs]
  (let [code (->str xs)]
    (alda "play" "--history" *alda-history* "--code" code)
    (alter-var-root #'*alda-history* str code \newline)
    code))

(defn stop!
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

   `instrument-call` can either be a map containing :names and an optional
   :nickname (e.g. {:names [\"piano\" \"trumpet\"] :nickname [\"trumpiano\"]})
   or a valid Alda instrument call string, e.g. \"piano/trumpet 'trumpiano'\".

   For convenience, single quotes can be used around the nickname instead of
   double quotes."
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
           (map {:flat \- :sharp \+ :natural \_} accidentals))))

(defn pitch
  [letter & accidentals]
  (map->LetterAndAccidentals {:letter letter :accidentals accidentals}))

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
              (map ->str components))))

(defrecord NoteLength [number dots]
  Stringify
  (-str [{:keys [number dots]}]
    (apply str number (repeat dots \.))))

(defn note-length
  [number & [{:keys [dots]}]]
  (map->NoteLength {:number number :dots (or dots 0)}))

(defrecord Milliseconds [number]
  Stringify
  (-str [{:keys [number]}]
    (str number "ms")))

(defn ms
  [number]
  (map->Milliseconds {:number number}))

(defn duration
  [& components]
  (map->Duration {:components components}))

(defrecord Note [pitch duration slurred?]
  Stringify
  (-str [{:keys [pitch duration slurred?]}]
    (str (->str pitch)
         (when duration (->str duration))
         (when slurred?
           (->str (->Slur))))))

(defn note
  "Causes every active instrument to play a note at its current offset for the
   specified duration.

   If no duration is specified, the note is played for the instrument's own
   internal duration, which will be the duration last specified on a note or
   rest in that instrument's part."
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
   instrument's part."
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
   events."
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

   `value` can be an octave number, :up or :down."
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
   events."
  []
  (->Barline))

(defrecord Marker [name]
  Stringify
  (-str [{:keys [name]}]
    (str \% name)))

(defn marker
  "Places a marker at the current absolute offset."
  [name]
  (map->Marker {:name name}))

(defrecord AtMarker [name]
  Stringify
  (-str [{:keys [name]}]
    (str \@ name)))

(defn at-marker
  "Sets the active instruments' current offset to the offset of the marker with
   the provided name."
  [name]
  (map->AtMarker {:name name}))

(defrecord Voice [number]
  Stringify
  (-str [{:keys [number]}]
    (str \V number \:)))

(defn voice
  "Begins a voice identified by `number`.

   Until the voice group ends, all voices are played simultaneously."
  [number]
  (map->Voice {:number number}))

(defrecord EndVoices []
  Stringify
  (-str [_] "V0:"))

(defn end-voices
  []
  "Ends the current voice group."
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
   current instrument."
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
   name."
  [var-name & events]
  (map->SetVariable {:name var-name :events events}))

(defrecord GetVariable [name]
  Stringify
  (-str [{:keys [name]}]
    name))

(defn get-variable
  "Returns any number of events previously defined as a variable."
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

(defmacro lisp-builtin
  [sym]
  `(defn ~sym
     ~(format "Emits inline Lisp code (%s ...)" sym)
     [& ~'args]
     (map->Sexp {:form (list* '~sym ~'args)})))

(defmacro lisp-builtins
  [& syms]
  (cons 'do
        (for [sym syms]
          `(lisp-builtin ~sym))))

(defmacro lisp-builtin-attributes
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


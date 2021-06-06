# Getting Started

## Setup

### Alda

First things first: you'll need to [install Alda][install-alda], if you haven't
done that already.

> alda-clj requires Alda version 2.0.0 or greater.

You can run `alda doctor` to verify that you have a working installation of
Alda.

### Clojure

If you don't already have `clojure` / `clj`, [install the Clojure command-line
tools][install-clojure].

There are several ways that you can set up a Clojure environment where you can
use alda-clj:

1. If you have an existing Clojure project that uses [Leiningen][leiningen] or
   [Boot][boot], you can add `[io.djy/alda-clj "LATEST"]` to the dependencies in
   your project.clj or build.boot.

2. You can start a REPL that includes alda-clj as a dependency:

   ```
   $ clj -Sdeps '{:deps {io.djy/alda-clj {:mvn/version "LATEST"}}}'
   Downloading: io/djy/alda-clj/maven-metadata.xml from https://repo.clojars.org/
   Downloading: io/djy/alda-clj/0.3.0/alda-clj-0.3.0.pom from https://repo.clojars.org/
   Downloading: io/djy/alda-clj/0.3.0/alda-clj-0.3.0.jar from https://repo.clojars.org/
   Clojure 1.10.1
   user=> (require '[alda.core :refer :all])
   nil
   user=> (alda "version")
   alda 2.0.0
   "alda 2.0.0\n"
   ```

3. You can write all of your Clojure code in a single file:

   ```
   $ cat demo.clj
   (require '[alda.core :refer :all])

   (alda "version")

   (System/exit 0)
   $ clojure -Sdeps '{:deps {io.djy/alda-clj {:mvn/version "LATEST"}}}' demo.clj
   alda 2.0.0
   ```

4. You can [make a new Clojure project][new-clj-project] that includes alda-clj
   as a dependency by creating a directory structure that looks like this...

   ```
   .
   ├── deps.edn
   └── src
       └── something.clj
   ```

   ...putting the following in `deps.edn`...

   ```clojure
   {:deps {io.djy/alda-clj {:mvn/version "LATEST"}}}
   ```

   ...and putting your Clojure code in `src/something.clj`...

   ```clojure
   (ns something
     (:require [alda.core :refer :all]))

   (defn -main
     []
     (alda "version")
     (System/exit 0))
   ```

   ...and then run the main function:

   ```bash
   $ clojure -m something
   alda 2.0.0
   ```

Once you're set up, you can follow along by copy-pasting the examples below into
your Clojure source file or REPL.

[install-alda]: https://alda.io/install
[install-clojure]: https://clojure.org/guides/getting_started
[leiningen]: https://leiningen.org/
[boot]: https://boot-clj.com/
[new-clj-project]: https://clojure.org/guides/deps_and_cli#_writing_a_program

## Basics

Each element of Alda's syntax has a corresponding function in [alda.core].

For example, the following Alda score:

```
piano:
  c8 d e f g a b > c
```

...can be produced via alda-clj by using the corresponding functions in
alda.core:

```clojure
(require '[alda.core :refer :all])

(part "piano")
(note (pitch :c) (note-length 8))
(note (pitch :d))
(note (pitch :e))
(note (pitch :f))
(note (pitch :g))
(note (pitch :a))
(note (pitch :b))
(octave :up)
(note (pitch :c))
```

But the code above doesn't actually _do_ anything. Each of the forms simply
returns a Clojure record representing an Alda _event_ such as a note, an octave
change, a part declaration, etc.

You can turn this into a score and play it by passing the events as arguments to
`alda.core/play!`:

```clojure
(play!
  (part "piano")
  (note (pitch :c) (note-length 8))
  (note (pitch :d))
  (note (pitch :e))
  (note (pitch :f))
  (note (pitch :g))
  (note (pitch :a))
  (note (pitch :b))
  (octave :up)
  (note (pitch :c)))
```

The return value of the `play!` form is the string of Alda code that was
generated and sent to the Alda client:

```clojure
"piano:\nc8 d e f g a b > c"
```

[alda.core]: https://cljdoc.org/d/io.djy/alda-clj/CURRENT/api/alda.core

You can always stop playback if things get out of hand:

```clojure
(stop!)
```

## Shelling out

You can conveniently issue arbitrary commands to the Alda client from the
comfort of your REPL:

```clojure
;; like running "alda version" in a terminal
(alda "version")

;; like running "alda doctor" in a terminal
(alda "doctor")

;; like running "alda instruments" in a terminal
(alda "instruments")
```

The output is printed on stdout and stderr, just like if you were running the
commands in a terminal.

If the command was successful (i.e. the exit code was 0), the full output on
stdout is also returned as a string, for convenience.

## Connecting to an Alda REPL server

By default, alda-clj is _not_ connected to an Alda REPL server. You can still
use `play!` to play scores, like we did above, however, each time you call
`play!`, the score is evaluated in a _separate context_.

For a better understanding of what this means, try evaluating the following
expressions, one at a time:

```clojure
;; Play a few notes on the guitar.
(play!
  (part "guitar")
  (note (pitch :e) (note-length 8))
  (note (pitch :f :sharp))
  (note (pitch :g)))

;; Play a couple more notes on the guitar?
(play!
  (note (pitch :a))
  (note (pitch :b) (note-length 2)))
```

Unless you are connected to an Alda REPL, you will only hear the notes in the
first expression. The second expression will run successfully, but you won't
hear anything. Why? Because the second expression is equivalent to running the
following `alda play` command at the command line:

```bash
alda play --code "a b2"
```

And no instrument is specified, so it makes no sound.

Wouldn't it be nice if you could play your score a little bit at a time? Well,
if you connect to an Alda REPL server, then you can do exactly that!

To start an Alda REPL server, run `alda repl --server` in your terminal. You
should see some output that tells you which port the server is running on:

```
nREPL server started on port 40105 on host localhost - nrepl://localhost:40105
```

Now, in your Clojure REPL (or program), use the `connect!` function to connect
to the Alda REPL server:

```clojure
;; Replace 40105 with the port that was printed when you started the Alda REPL
;; server.
(connect! {:port 40105})
```

If you started both your Clojure REPL/program and your Alda REPL server in the
same directory, you can omit the port argument and alda-clj will figure it out
by reading a file created by the Alda REPL server (`.alda-nrepl-port`).

```clojure
(connect!)
```

Now, you can write and play your scores incrementally!

```clojure
;; Conjure a guitar.
(play!
  (part "guitar"))

;; Play a few notes on the guitar.
(play!
  (note (pitch :e) (note-length 8))
  (note (pitch :f :sharp))
  (note (pitch :g)))

;; Play a couple more notes, still on the guitar.
(play!
  (note (pitch :a))
  (note (pitch :b) (note-length 2)))
```

Between invocations of `play!`, the Alda REPL server is keeping track of which
instrument(s) are active and all of their properties (octave, volume, panning,
etc.) so that the context is not lost. This is useful for live coding, as well
as for quickly trying things out when you're composing a score.

Whenever you want to erase this context and start over from a clean slate, you
can reset the state of the Alda REPL server:

```clojure
(new-score!)
```

To see the Alda code that has been evaluated so far in your current score:

```clojure
(println (score-text))
```

## Notes

The `note` function takes as arguments a pitch and (optionally) a duration.

```clojure
;; a D
(play!
  (note (pitch :d)))
;;=> "d"

;; an E eighth note
(play!
  (note (pitch :e) (note-length 8)))
;;=> "e8"

;; a C# whole note tied to a dotted half note
(play!
  (note (pitch :c :sharp)
        (duration (note-length 1)
                  (note-length 2 {:dots 1}))))
;;=> "c+1~2."
```

### Pitch

The `pitch` function can be used to construct a pitch out of a letter and any
number of accidentals (flats and sharps):

```clojure
(play!
  (note (pitch :b)))
;;=> "b"

(play!
  (note (pitch :b :flat)))
;;=> "b-"

(play!
  (note (pitch :f :sharp)))
;;=> "f+"

(play!
  (note (pitch :f :sharp :sharp)))
;;=> "f++"
```

### Duration

A simple example of a duration is a single note-length:

```clojure
;; a C half note
(play!
  (note (pitch :c) (note-length 2)))
;;=> "c2"
```

Alda also allows you to specify the length of a note in milliseconds:

```clojure
;; a C note lasting 423 ms
(play!
  (note (pitch :c) (ms 423)))
;;=> "c423ms"
```

Durations (whether they be `note-length` or `ms`) can be added together via the
`duration` function:

```clojure
;; A double-dotted quarter note, tied to an eighth note, tied to a note 456ms
;; long, tied to a "sixth" note. Try writing THAT in standard musical notation!
(play!
  (note (pitch :c)
        (duration (note-length 4 {:dots 2})
                  (note-length 8)
                  (ms 456)
                  (note-length 6))))
;;=> "c4..~8~456ms~6"
```

## Rests

The function for a rest in alda.core is called `pause`, so as not to conflict
with the super popular function `clojure.core/rest`:

```clojure
(play!
  (note (pitch :f) (note-length 4))
  (pause (note-length 4))
  (note (pitch :f) (note-length 4))
  (pause (note-length 4))
  (note (pitch :f) (note-length 4))
  (pause (note-length 4)))
;;=> "f4 r4 f4 r4 f4 r4"
```

## Chords

Wrap notes in a `chord` form to create a chord:

```clojure
(play!
  (chord
    (note (pitch :c) (note-length 1))
    (note (pitch :e :flat))
    (note (pitch :g))
    (note (pitch :b))))
;;=> "c1 / e- / g / b"
```

## Strings and sequences

Ultimately, all we're doing here is generating a string of Alda code and sending
it over to the Alda client.

Because each event just ends up being a string anyway, `play!` will happily
accept a string of Alda code in lieu of an event:

```clojure
(play!
  "piano: o4"
  "c16/e/g"
  (note (pitch :a))
  (note (pitch :b))
  "> c")
;;=> "piano: o4 c16/e/g a b > c"
```

For convenience, `play!` will also flatten the sequence of its arguments. This
means you can make liberal use of the handy sequence functions in Clojure's
standard library, without needing to worry about providing `play!` with a flat
sequence of events.

```clojure
(play!
  (part "piano")
  (for [t (repeatedly 4 #(+ 60 (rand-int 200)))]
    [(tempo t)
     (for [letter [:c :d :e :f :g]]
       (note (pitch letter) (note-length 8)))]))
;;=> "piano:\n(tempo 72) c8 d8 e8 f8 g8 (tempo 157) c8 d8 e8 f8 g8 (tempo 129) c8 d8 e8 f8 g8 (tempo 96) c8 d8 e8 f8 g8"
```

## Inline Lisp forms

The Alda language itself includes Lisp forms as a syntactic feature. This is
used to provide language features without needing to provide additional syntax.
For example, here is how you set the global tempo in an Alda score:

```
(tempo! 160)

piano: c8 d e f g
```

alda-clj allows code like this to be generated in a couple of different ways.
One way is that you can pass quoted S-expressions into `play!` and they will be
emitted verbatim into the string of generated Alda code:

```clojure
(play!
  '(tempo! 160)
  (part "piano")
  (note (pitch :c) (note-length 8))
  (note (pitch :d))
  (note (pitch :e))
  (note (pitch :f))
  (note (pitch :g)))
;;=> "(tempo! 160) piano:\nc8 d e f g"
```

Another way is that alda.core includes convenience functions for the functions
like `tempo!` that are available in the Alda Lisp environment. Whenever
alda.core includes one of these functions, you can simply call it and it will
have the same effect as emitting the S-expression directly into the generated
Alda code:

```clojure
(play!
  (tempo! 160)
  (part "piano")
  (note (pitch :c) (note-length 8))
  (note (pitch :d))
  (note (pitch :e))
  (note (pitch :f))
  (note (pitch :g)))
;;=> "(tempo! 160) piano:\nc8 d e f g"
```

## Other stuff

alda-clj supports every feature of the Alda language, including things not
covered here like [cram brackets][cram], [variables], and [markers].

The [API docs][api-docs] provide a reference of what functions are available in
alda.core and how they are used.

[cram]: https://cljdoc.org/d/io.djy/alda-clj/CURRENT/api/alda.core#cram
[variables]: https://cljdoc.org/d/io.djy/alda-clj/CURRENT/api/alda.core#set-variable
[markers]: https://cljdoc.org/d/io.djy/alda-clj/CURRENT/api/alda.core#marker
[api-docs]: https://cljdoc.org/d/io.djy/alda-clj/CURRENT/api/alda

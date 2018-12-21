# alda-clj

[![Clojars Project](https://img.shields.io/clojars/v/io.djy/alda-clj.svg)](https://clojars.org/io.djy/alda-clj)

[![cljdoc badge](https://cljdoc.org/badge/io.djy/alda-clj)](https://cljdoc.org/d/io.djy/alda-clj/CURRENT)

A Clojure library for live-coding music with [Alda](https://alda.io).

## Background and rationale

See [history.md](doc/history.md) for a long-winded account of the path that led
to alda-clj.

## How does it work?

alda-clj is a Clojure DSL that is almost identical to the `alda.lisp` DSL built
into the runtime of Alda 1.x. But instead of being part of the process that
generates and plays the Alda score, **alda-clj generates a string of Alda code
and sends it to the `alda` client**.

The idea is simple: `alda` is just a low-level tool for playing a score that's
already been written (or generated). When you want to go higher-level and create
scores programmatically, you can do so in an external process and pipe the
result to `alda`. alda-clj is one way to do this.

## Usage

Add the latest version in Clojars to your dependencies:

```clojure
;; deps.edn
io.djy/alda-clj {:mvn/version "X.X.X"}

;; lein/boot
[io.djy/alda-clj "X.X.X"]
```

If you haven't already, [install
Alda](https://github.com/alda-lang/alda#installation) and make sure `alda` is
available on your `PATH`.

> alda-clj will shell out and use `alda` (wherever it's found on your `PATH`) to
> play your scores. If desired, you can specify an alternate `alda` executable
> by binding `alda.core/*alda-executable*` to something else, e.g.
> `"/home/dave/Downloads/some-other-alda"`.

Require `alda.core` and you're off to the races!

```clojure
(require '[alda.core :refer :all])

(play!
  (part "piano")
  (for [notes [[:c :e :g] [:c :f :a] [:c :e :g]]]
    (apply chord (map #(note (pitch %)) notes))))
```

Each time you successfully `play!` something, the generated Alda code is
appended to `alda.core/*alda-history*`, a string of Alda code representing the
score so far that is sent along for context on each call to the `alda` client.
This is what allows scores to be played incrementally, e.g.:

```clojure
;; conjure a guitar
(play! (part "guitar"))

;; play a few notes on the guitar
(play!
  (note (pitch :e) (note-length 8))
  (note (pitch :f :sharp))
  (note (pitch :g)))

;; play a few notes, still on the guitar
(play!
  (notes (pitch :a))
  (notes (pitch :b) (note-length 2)))
```

Between invocations of `play!`, the Alda client "remembers" which instrument(s)
were active and all of their properties (octave, volume, panning, etc.) so that
the context is not lost.

The history can be cleared at will:

```clojure
(clear-history!)
```

You can also stop playback if things get out of hand:

```clojure
(stop!)
```

You can conveniently issue arbitrary commands to the Alda client from the
comfort of your REPL:

```clojure
(println (alda "version"))
(println (alda "status"))
```

## Docs, examples

[Detailed documentation is available at
cljdoc](https://cljdoc.org/d/io.djy/alda-clj/CURRENT).

There are also [example scripts](examples) in this repo.

## License

Copyright © 2018 Dave Yarwood

Distributed under the Eclipse Public License version 2.0.

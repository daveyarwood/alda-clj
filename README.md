# alda-clj

[![Clojars Project](https://img.shields.io/clojars/v/io.djy/alda-clj.svg)](https://clojars.org/io.djy/alda-clj)

[![cljdoc badge](https://cljdoc.org/badge/io.djy/alda-clj)](https://cljdoc.org/d/io.djy/alda-clj/CURRENT)

alda-clj is a Clojure library for algorithmic music composition and live-coding
with [Alda](https://alda.io).

A simple Clojure DSL provides useful functions like `note` and `chord` that can
be composed together to create a musical score. The resulting values are
translated into a string of Alda code and played in a subprocess via the `alda`
command line client.

alda-clj is intended to be used in at least two ways:

1. In an **improv / live-coding** setting, using a live Clojure REPL to generate
   and play music on the fly.

2. In a **music composition** setting, where the composition is a Clojure
   program / script that uses alda-clj to generate a score.

## Usage

> **NOTE**: alda-clj requires Alda version 2.0.0 or greater.

1. If you haven't already, [install Alda](https://alda.io/install) and make sure
   `alda` is available on your `PATH`.

  > alda-clj will shell out and use `alda` (wherever it's found on your `PATH`) to
  > play your scores. If desired, you can specify an alternate `alda` executable
  > by binding `alda.core/*alda-executable*` to something else, e.g.
  > `"/home/dave/Downloads/some-other-alda"`.

2. Add the latest release version of alda-clj to your dependencies:

  ```clojure
  ;; deps.edn
  io.djy/alda-clj {:mvn/version "X.X.X"}

  ;; lein/boot
  [io.djy/alda-clj "X.X.X"]
  ```

3. Require `alda.core` and you're off to the races!

  ```clojure
  (require '[alda.core :refer :all])

  (play!
    (part "piano")
    (for [notes [[:c :e :g] [:c :f :a] [:c :e :g]]]
      (apply chord (map #(note (pitch %)) notes))))
  ```

## Docs, examples, etc.

[API documentation](https://cljdoc.org/d/io.djy/alda-clj/CURRENT/api/alda), a
[Getting Started](https://cljdoc.org/d/io.djy/alda-clj/CURRENT/doc/getting-started) guide and
more are available at cljdoc.

There are also [example scripts](examples) in this repo that will give you a
sense of what you can do with alda-clj.

Ping `@dave` on [Slack](https://slack.alda.io) if you have any questions or if
you just want to chat about alda-clj!

## License

Copyright © 2018-2023 Dave Yarwood

Distributed under the Eclipse Public License version 2.0.

# alda-clj

[![Clojars Project](https://img.shields.io/clojars/v/io.djy/alda-clj.svg)](https://clojars.org/io.djy/alda-clj)

A Clojure library for live-coding music with [Alda](https://alda.io).

## History

In 2012, I designed Alda with a singular focus on the language being
approachable for beginners, even those with little to no knowledge of
programming.

In 2014-2016, with help from contributors, there was a huge spike in the
language's development. Along the way, we had the idea to include Clojure
S-expressions as a first-class syntactical feature of Alda. The Alda runtime is
a Clojure process that parses and builds a score from Alda code, which can
include the original markup-style syntax as well as inline Clojure code; both
styles can represent the same musical events, and they can be mixed together
within an Alda score, but only one of the styles (inline Clojure code) is a
Turing-complete programming language that allows you to build a score
programmatically or algorithmically, which can yield very interesting and fun
results.

> There are a couple of videos online of me demonstrating how I use inline
> Clojure code in Alda to generate interesting musical scores by using
> functional programming techniques. If you'd like to see this in action, I
> recommend the live demo portions of the talks that I gave in [Japan in
> 2016][farm2016] and [Australia in 2018][compose2018].

[farm2016]: https://youtu.be/c5pCFtwO4j8?t=374
[compose2018]: https://youtu.be/7nbBSwopG-E?t=593

I've grown really attached to this aspect of creating music with Alda. I think
there are some very pleasing parallels between writing FP code in Clojure and
composing music of a certain style.

There ended up being some problems with inline Clojure code evaluation being
baked into the Alda language as feature. Namely:

1. It locks in Clojure as a required language runtime for implementing Alda.
   This has some drawbacks:

   a. The thing doing the audio performance must be a JVM. This means we cannot,
      for example, implement a port of Alda that runs in the browser. (Okay,
      technically we could do it in ClojureScript, but the semantics of eval in
      ClojureScript are complicated, so it would be a bit tricky.)

   b. The Clojure runtime has a noticeably long startup time, which makes it
      less than ideal for implementing command-line applications. This is the
      reason that we ended up moving Alda to a client/server architecture, with
      a lighter-weight Java client talking to a persistent Clojure server. This
      is a bit awkward, as it forces Alda users to know and care about a server
      being up in order to use the `alda` CLI.

2. It adds complexity to the language. On one hand, having inline Clojure code
   as a built-in feature has allowed us to add all kinds of complex and
   interesting features to Alda without needing to pollute the language with
   additional syntax. But on the other hand, having this kind of flexibility has
   invited us to think of Alda as a platform that includes a Clojure runtime,
   instead of a simple music composition language that's approachable for
   beginners.

3. If you're an Alda user who is interested in leveraging the Clojure runtime,
   you are limited in that you can't fully control the code execution context
   (the `alda.lisp` DSL is referred in by default), you can't bring in
   additional dependencies, and you can't easily see the output or return values
   of the Clojure forms being evaluated.

4. One might be tempted to run an Alda server out in the open and have remote
   clients connect to it and collaboratively write and play music, but that
   presents significant security concerns when the clients can just send over
   arbitrary Clojure code to be eval'd. I sincerely hope that no one is using
   Alda this way today! But if we were to remove the inline Clojure code
   feature, that would mitigate the security concerns and allow this sort of
   thing to happen in a safe way.

After thinking about these things for a good long while, I've come to the
conclusion that Alda should be simplified to be just the parts where it excels,
and whenever it's desired, additional complexity can be added orthogonally with
external tools.

But I still want to live-code Alda by writing Clojure code!

Luckily, I found a way to still do that, but in a more sensible way that offers
all of the same features, but doesn't require Alda to be implemented in Clojure
or allow one to execute arbitrary Clojure code inside the Alda server process.

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

## Docs, examples

For more details, see ... TODO ...

## License

Copyright © 2018 Dave Yarwood

Distributed under the Eclipse Public License version 2.0.

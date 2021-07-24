# CHANGELOG

## 0.3.1 (2021-07-24)

Fixed a build issue where the deployed version of alda-clj 0.3.0 wasn't
including the right dependencies because of a stale pom.xml.

I am now generating the pom.xml every time before deploying to Clojars, so this
issue hopefully won't happen again!

## 0.3.0 (2021-06-30)

This release coincides with the release of Alda 2.0.0. :tada:

### Breaking changes

* **alda-clj now requires Alda version 2.0.0 or greater.**

* The syntax for `key-signature` and friends (`key-sig`, `key-sig!`,
  `key-signature!`) has changed slightly, corresponding to the same change in
  Alda itself in Alda 2. See the [Alda 2 migration guide][alda-2-syntax-changed]
  for more information about this change. To summarize:

  ```clojure
  ;; Alda 1 and 2 (still works)
  (key-sig "f+ c+ g+")

  ;; Alda 1 (no longer works in alda-clj)
  (key-sig! [:a :major])
  (key-sig! {:f [:sharp] :c [:sharp] :g [:sharp]})

  ;; Alda 2
  (key-sig '(a major))
  (key-sig '(f (sharp) c (sharp) g (sharp)))
  ```

  Everything else about the syntax in Alda 2 and alda-clj remains the same.

* Removed `*alda-history*` and the `clear-history` function, as they are no
  longer relevant. See below about connecting to an Alda REPL server to preserve
  context between evaluations.

### Improvements

* The `alda` function (as well as the functions that depend on it, including
  `play!`) now prints stdout and stderr output from Alda as it is produced. The
  stdout output is still returned at the end as a string.

### New features

* You can connect to an Alda REPL server (a new feature of Alda 2) via the new
  `connect!` function. There is also a `disconnect!` if you want to undo that
  and revert to the default, out-of-the box behavior, where `alda play` is used
  to evaluate and play scores in a separate context.

  Connecting to an Alda REPL server is recommended, as it allows you to evaluate
  subsequent snippets of Alda code within the same context. This is great for
  interactive development and live coding. I've updated the [Getting
  Started][getting-started] guide with more details about this new workflow, so
  check that out!

* Added `score-text` and `new-score!` functions to display the current score
  text and start a new score, respectively. These functions can only be used
  when connected to an Alda REPL server.

* Added a `send-nrepl-message!` function that most of you won't ever need to
  use, but if you ever do, it's there! `send-nrepl-message!` is used internally
  by functions like `play!` when you're connected to an Alda REPL server.

* Added a `parse-events` function that takes any combination of strings of Alda
  code and alda-clj event records, runs them through `alda parse --output
  events`, and returns a sequence of alda-clj event records that results from
  translating the JSON output.

  Thanks, [@wcerfgba][wcerfgba], for the contribution!

## 0.2.3 (2020-01-27)

No change; bumped version to publish improvements to the Getting Started docs.

## 0.2.2 (2019-08-17)

* Implemented the LispForm protocol on Milliseconds note duration objects. This
  fixes a bug where `midi-note` and `ms` couldn't be used together, e.g.
  `(note (midi-note 60) (ms 2000))`.

## 0.2.1 (2019-08-17)

* Added `midi-note` function that can be used to specify the pitch of a note as
  a MIDI note number, instead of `pitch` which specifies pitch as a letter and
  accidentals.

* Implementation detail: added a LispForm protocol and `->lisp-form` wrapper
  function. Objects that implement the LispForm protocol are representable in
  alda-lisp, and `->lisp-form` returns the corresponding Lisp S-expression for
  that object.

  These S-expressions are rendered directly into Alda as opposed to having a
  syntax representation like `c+`. This allows us to make use of Alda features
  that are implemented only in Lisp to avoid adding additional syntax to the
  language.

  Example: `(->lisp-form (note (midi-note 42))) => (note (midi-note 42))`

## 0.1.8 (2019-02-21)

No change; bumped version to tweak the docs.

## 0.1.7 (2019-01-21)

No change; bumped version to make more tweaks/fixes to the docs.

## 0.1.6 (2019-01-21)

No change; bumped version to make some tweaks to documentation.

## 0.1.5 (2019-01-21)

* Bugfix: `cram` duration can be `nil` (i.e. the default duration) now.

* Emitted Alda code formatting improvement: instrument calls now go on a new
  line.

* Added a ton of docstrings! I'm not 100% sure the formatting is correct, so
  some parts might end up looking wonky. I don't really have a good way to test
  how the docs will look before I deploy them, and the docs are tied to
  releases, so I guess I'll just have to push additional releases if anything
  documentation-related needs fixing. ¯\\\_(ツ)\_/¯

## 0.1.4 (2018-12-22)

No change; bumped version to update documentation via cljdoc.

## 0.1.3 (2018-12-21)

No change; bumped version while tinkering with cljdoc.

## 0.1.2 (2018-12-13)

* Bugfix: `(octave :up)` generates `>`, not `(octave :up)`

## 0.1.1 (2018-12-11)

No change; I had to bump the version while tinkering with deployment setup.

## 0.1.0 (2018-12-11)

Initial release. I think things are relatively stable, but report any issues you
may run into!

[wcerfgba]: https://github.com/wcerfgba
[getting-started]: https://cljdoc.org/d/io.djy/alda-clj/CURRENT/doc/getting-started
[alda-2-syntax-changed]: https://github.com/alda-lang/alda/blob/master/doc/alda-2-migration-guide.md#attribute-syntax-has-changed-in-some-cases

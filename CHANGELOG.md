# CHANGELOG

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


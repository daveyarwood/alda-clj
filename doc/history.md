# History

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
> 2016][farm2016], [Australia in 2018][compose2018], and [St. Louis in
> 2019][strangeloop2019].

[farm2016]: https://youtu.be/c5pCFtwO4j8?t=374
[compose2018]: https://youtu.be/7nbBSwopG-E?t=593
[strangeloop2019]: https://youtu.be/6hUihVWdgW0?t=1573

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

**But I still want to live-code Alda by writing Clojure code!**

Luckily, I found a way to still do that, but in a more sensible way that offers
all of the same features, but it doesn't require Alda to be implemented in
Clojure or allow one to execute arbitrary Clojure code inside the Alda server
process.


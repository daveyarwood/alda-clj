(ns alda.core
  (:require [clj-kondo.hooks-api :as api]))

(defmacro ^:private lisp-builtin
  [sym]
  ;; `(defn ~sym
  ;;    "placeholder docstring"
  ;;    [& ~'args]
  ;;    "placeholder value")
  `(defn ~sym
     ~(format "Emits inline Lisp code `(%s ...)`" sym)
     [& ~'args]
     ;; These don't seem to work, but it doesn't really matter that this macro
     ;; returns _exactly_ what the real one in alda.core does; the important
     ;; thing is just that we `def` something.
     (map->Sexp {:form (list* '~sym ~'args)})))
     ;; (alda.core/map->Sexp {:form (list* '~sym ~'args)})
     ;;
     ;; ...These don't seem to work either, though:
     ;; {:form (list* '~sym ~'args)}))
     ;; "placeholder value"))

(defmacro ^:private lisp-builtins
  [& syms]
  (if (> (count syms) 1)
    (cons 'do
          (for [sym syms]
            `(lisp-builtin ~sym)))
    `(lisp-builtin ~(first syms))))

(defmacro ^:private lisp-builtin-attributes
  [& syms]
  (if (> (count syms) 1)
    (cons 'do
          (for [sym syms]
            `(lisp-builtins ~sym ~(symbol (str sym \!)))))
    `(lisp-builtins ~(first syms) ~(symbol (str ~(first syms) \!)))))

(defn transform-lisp-builtin
  ""
  [{:keys [node]}]
  {:node (doto (api/macroexpand lisp-builtin node) prn)})

(defn transform-lisp-builtins
  ""
  [{:keys [node]}]
  {:node (doto (api/macroexpand lisp-builtins node) prn)})

(defn transform-lisp-builtin-attributes
  ""
  [{:keys [node]}]
  {:node (doto (api/macroexpand lisp-builtin-attributes node) prn)})

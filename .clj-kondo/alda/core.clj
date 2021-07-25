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
  (cons 'do
        (for [sym syms]
          `(lisp-builtin ~sym))))

(defmacro ^:private lisp-builtin-attributes
  [& syms]
  (cons 'do
        (for [sym syms]
          `(lisp-builtins ~sym ~(symbol (str sym \!))))))

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

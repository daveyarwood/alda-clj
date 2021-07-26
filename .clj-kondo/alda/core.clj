(ns alda.core
  (:require [clj-kondo.hooks-api :as api]))

(defmacro ^:private lisp-builtin
  [sym]
  `(defn ~sym
     ~(format "Emits inline Lisp code `(%s ...)`" sym)
     [& ~'args]
     (map->Sexp {:form (list* '~sym ~'args)})))

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

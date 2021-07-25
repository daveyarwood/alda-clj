(ns hooks
  (:require [clj-kondo.hooks-api :as api]))

(defn- assert-symbol!
  "Checks whether a `node` is a symbol.

   If it isn't, registers a finding and also returns it."
  [node]
  (when-not (symbol? (api/sexpr node))
    (let [finding (merge
                    {:type    :type-mismatch
                     :message "Argument must be a symbol."}
                    (meta node))]
      (api/reg-finding! finding)
      finding)))

(defn- assert-arity!
  "Checks the number of arguments in a `list-node` against the expected `arity`.

   If there is an arity mismatch, registers a finding and also returns it."
  [arity list-node]
  (let [args       (rest (:children list-node))
        args-count (count args)]
    (when-not (= arity args-count)
      (let [finding (merge
                      {:type    :invalid-arity
                       :message (format "Expected %d arg(s), but got %d"
                                        arity
                                        (count args))}
                      (meta list-node))]
        (api/reg-finding! finding)
        finding))))

(defn lisp-builtin
  "Defines a alda-clj function corresponding to a built-in alda-lisp function.

   Example usage:

   (lisp-builtin set-duration)"
  [{:keys [node]}]
  (let [token  (second (:children node))
        errors (remove nil? [(assert-arity! 1 node)
                             (assert-symbol! token)])]
    (when (empty? errors)
      {:node
       (let [sym (api/sexpr token)]
         (api/list-node
           [(api/token-node 'defn)
            (api/token-node sym)
            (api/token-node
              (format "Emits inline Lisp code `(%s ...)" sym))
            (api/vector-node
              [(api/token-node '&)
               (api/token-node 'args)])
            (api/list-node
              [(api/token-node 'alda.core/map->Sexp)
               (api/map-node
                 [(api/keyword-node :form)
                  (api/list-node
                    [(api/token-node 'list*)
                     (api/list-node
                       [(api/token-node 'quote)
                        (api/token-node sym)])
                     (api/token-node 'args)])])])]))})))

(defn lisp-builtins
  "Defines alda-clj functions corresponding to built-in alda-lisp functions.

   Example usage:

   (lisp-builtins
     set-duration set-note-length)"
  [{:keys [node]}]
  (let [tokens (rest (:children node))
        errors (->> tokens
                    (map assert-symbol!)
                    (remove nil?))]
    (when (empty? errors)
      {:node
       (api/list-node
         (cons
           (api/token-node 'do)
           (for [token tokens
                 :let [sym (api/sexpr token)]]
             (api/list-node
               [(api/token-node 'alda.core/lisp-builtin)
                (api/token-node sym)]))))})))

(defn lisp-builtin-attributes
  "Defines alda-clj functions corresponding to built-in alda-lisp attributes.

   Example usage:

   (lisp-builtin-attributes
     tempo quant volume panning)"
  [{:keys [node]}]
  (let [tokens (rest (:children node))
        errors (->> tokens
                    (map assert-symbol!)
                    (remove nil?))]
    (when (empty? errors)
      {:node
       (api/list-node
         (cons
           (api/token-node 'do)
           (for [token tokens
                 :let [sym (api/sexpr token)]]
             (api/list-node
               [(api/token-node 'alda.core/lisp-builtins)
                (api/token-node sym)
                (api/token-node (symbol (str sym "!")))]))))})))

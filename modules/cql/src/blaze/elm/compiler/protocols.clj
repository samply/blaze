(ns blaze.elm.compiler.protocols)

(defprotocol Expression
  (-eval [this context resource scope])
  (-hash [this]))


(defn expr? [x]
  (satisfies? Expression x))

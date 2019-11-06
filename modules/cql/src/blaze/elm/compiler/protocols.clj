(ns blaze.elm.compiler.protocols)


(defprotocol Expression
  (-eval [this context resource scope])
  (-hash [this]))


(defn expr? [x]
  (satisfies? Expression x))


(extend-protocol Expression
  nil
  (-eval [this _ _ _]
    this)
  (-hash [this]
    this)

  Object
  (-eval [this _ _ _]
    this)
  (-hash [this]
    this))

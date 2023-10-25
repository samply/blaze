(ns blaze.elm.expression.cache.protocols)


(defprotocol Cache
  (-get [cache expression])
  (-list [cache])
  (-total [cache]))

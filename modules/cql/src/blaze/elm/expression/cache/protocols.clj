(ns blaze.elm.expression.cache.protocols)

(defprotocol Cache
  (-get [cache expression])
  (-list-by-t [cache])
  (-total [cache]))

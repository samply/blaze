(ns blaze.elm.expression.cache.protocols)

(defprotocol Cache
  (-get [cache expression])
  (-get-disk [cache hash])
  (-delete-disk [cache hash])
  (-list-by-t [cache])
  (-total [cache]))

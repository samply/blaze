(ns blaze.elm.data-provider)


(defprotocol DataProvider
  (compile-path [this path])
  (resolve-property [this target compiled-path]))

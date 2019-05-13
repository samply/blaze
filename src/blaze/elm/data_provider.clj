(ns blaze.elm.data-provider
  (:require
    [clojure.spec.alpha :as s]))


(defprotocol DataProvider
  (compile-path [this path])
  (resolve-property [this target compiled-path]))

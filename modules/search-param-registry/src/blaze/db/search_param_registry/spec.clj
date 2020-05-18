(ns blaze.db.search-param-registry.spec
  (:require
    [blaze.db.search-param-registry :as sr]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/search-param-registry
  #(satisfies? sr/SearchParamRegistry %))


;; TODO: need to have an compiled :expression but the keys-form doesnt work with records
(s/def :blaze.db/search-param
  some?)

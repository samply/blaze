(ns blaze.db.search-param-registry.spec
  (:require
    [blaze.db.impl.protocols :as p]
    [blaze.db.search-param-registry :as sr]
    [clojure.spec.alpha :as s]))


(defn search-param-registry? [x]
  (satisfies? sr/SearchParamRegistry x))


(s/def :blaze.db/search-param-registry
  search-param-registry?)


(s/def :blaze.db/search-param
  #(satisfies? p/SearchParam %))


(s/def :blaze.db.search-param/modifier
  string?)

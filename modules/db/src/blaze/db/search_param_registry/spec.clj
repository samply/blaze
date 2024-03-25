(ns blaze.db.search-param-registry.spec
  (:require
   [blaze.db.impl.protocols :as p]
   [blaze.db.search-param :as-alias search-param]
   [clojure.spec.alpha :as s]))

(defn search-param-registry? [x]
  (satisfies? p/SearchParamRegistry x))

(s/def :blaze.db/search-param-registry
  search-param-registry?)

(s/def :blaze.db/search-param
  #(satisfies? p/SearchParam %))

(s/def ::search-param/modifier
  string?)

(s/def :blaze.db.search-param-registry/extra-bundle-file
  (s/nilable string?))

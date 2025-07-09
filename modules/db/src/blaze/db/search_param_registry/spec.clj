(ns blaze.db.search-param-registry.spec
  (:require
   [blaze.db.impl.protocols :as p]
   [blaze.db.search-param :as-alias search-param]
   [blaze.db.search-param-registry :as-alias sr]
   [clojure.spec.alpha :as s]))

(s/def :blaze.db/search-param-registry
  #(satisfies? p/SearchParamRegistry %))

(s/def :blaze.db/search-param
  #(satisfies? p/SearchParam %))

(s/def :blaze.db/search-param-with-ordered-index-handles
  (s/and :blaze.db/search-param #(satisfies? p/WithOrderedIndexHandles %)))

(s/def ::search-param/modifier
  string?)

(s/def ::sr/extra-bundle-file
  (s/nilable string?))

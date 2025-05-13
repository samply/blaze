(ns blaze.terminology-service.local.spec
  (:require
   [blaze.path.spec]
   [blaze.terminology-service.local :as-alias local]
   [clojure.spec.alpha :as s])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(s/def ::local/graph-cache
  #(instance? Cache %))

(s/def ::local/enable-bcp-13
  boolean?)

(s/def ::local/enable-bcp-47
  boolean?)

(s/def ::local/enable-ucum
  boolean?)

(s/def ::local/loinc
  map?)

(s/def ::local/sct
  map?)

(s/def ::local/num-concepts
  nat-int?)

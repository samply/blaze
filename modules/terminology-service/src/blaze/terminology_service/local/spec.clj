(ns blaze.terminology-service.local.spec
  (:require
   [blaze.path.spec]
   [blaze.terminology-service.local :as-alias local]
   [clojure.spec.alpha :as s]))

(s/def ::local/enable-bcp-13
  boolean?)

(s/def ::local/enable-ucum
  boolean?)

(s/def ::local/loinc
  map?)

(s/def ::local/sct
  map?)

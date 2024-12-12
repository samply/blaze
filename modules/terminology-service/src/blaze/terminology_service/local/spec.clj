(ns blaze.terminology-service.local.spec
  (:require
   [blaze.path.spec]
   [blaze.terminology-service.local :as-alias local]
   [clojure.spec.alpha :as s]))

(s/def ::local/enable-ucum
  boolean?)

(s/def ::local/sct-release-path
  :blaze/dir)

(ns blaze.terminology-service.extern.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.terminology-service.extern/base-uri
  string?)

(ns blaze.terminology-service.extern.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [java.net.http HttpClient]))


(s/def :blaze.terminology-service.extern/base-uri
  string?)


(s/def :blaze.terminology-service.extern/http-client
  #(instance? HttpClient %))

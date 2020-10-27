(ns blaze.fhir-client.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [java.net.http HttpClient]))


(s/def :java.net.http/http-client
  #(instance? HttpClient %))


(s/def :blaze.fhir-client/base-uri
  string?)


(s/def :blaze.fhir-client/client
  (s/keys :req-un [:java.net.http/http-client :blaze.fhir-client/base-uri]))

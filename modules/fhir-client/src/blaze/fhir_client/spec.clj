(ns blaze.fhir-client.spec
  (:require
    [blaze.http-client.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.fhir-client/options
  (s/alt :map (s/keys :opt-un [:blaze/http-client])
         :kv-coll (s/keys* :opt-un [:blaze/http-client])))

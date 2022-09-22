(ns blaze.fhir.spec.spec
  (:require
    [blaze.fhir.spec.impl]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))


(s/def :fhir.resource/type
  (s/and string? #(re-matches #"[A-Z]([A-Za-z0-9_]){0,254}" %)))


(s/def :fhir/type
  (s/and
    keyword?
    #(some-> (namespace %) (str/starts-with? "fhir"))
    #(re-matches #"[A-Za-z]([A-Za-z0-9_]){0,254}" (name %))))


(s/def :blaze.resource/id
  (s/and string? #(re-matches #"[A-Za-z0-9\-\.]{1,64}" %)))


(s/def :blaze.fhir/local-ref-tuple
  (s/tuple :fhir.resource/type :blaze.resource/id))


(s/def :blaze.fhir/local-ref
  (s/and string?
         (s/conformer #(str/split % #"/" 2))
         :blaze.fhir/local-ref-tuple))


(s/def :blaze/resource
  #(s2/valid? :fhir/Resource %))

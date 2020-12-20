(ns blaze.fhir.spec.spec
  (:require
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))


(s/def :fhir.type/name
  (s/and string? #(re-matches #"[A-Z]([A-Za-z0-9_]){0,254}" %)))


(s/def :fhir/type
  (s/and
    keyword?
    #(some-> (namespace %) (str/starts-with? "fhir"))
    #(s/valid? :fhir.type/name (name %))))


(s/def :blaze.resource/id
  (s/and string? #(re-matches #"[A-Za-z0-9\-\.]{1,64}" %)))


(s/def :blaze.fhir/local-ref
  (s/and string?
         (s/conformer #(str/split % #"/" 2))
         (s/tuple :fhir.type/name :blaze.resource/id)))


(s/def :blaze/resource
  #(s2/valid? :fhir/Resource %))

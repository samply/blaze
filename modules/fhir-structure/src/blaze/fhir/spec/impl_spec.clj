(ns blaze.fhir.spec.impl-spec
  (:require
    [blaze.fhir.spec.impl :as impl]
    [clojure.spec.alpha :as s])
  (:import
    [java.util.regex Pattern]))


(s/fdef impl/xml-value-matches?
  :args (s/cat :regex #(instance? Pattern %) :element impl/element?)
  :ret boolean?)


(s/fdef impl/primitive-type->spec-defs
  :args (s/cat :struct-def (comp #{"primitive-type"} :kind)))

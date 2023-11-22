(ns blaze.fhir.spec.impl.xml-spec
  (:require
   [blaze.fhir.spec.impl.xml :as xml]
   [clojure.spec.alpha :as s])
  (:import
   [java.util.regex Pattern]))

(s/fdef xml/value-matches?
  :args (s/cat :regex #(instance? Pattern %) :element xml/element?)
  :ret boolean?)

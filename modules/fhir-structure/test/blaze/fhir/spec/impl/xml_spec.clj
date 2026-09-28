(ns blaze.fhir.spec.impl.xml-spec
  (:require
   [blaze.fhir.spec.impl.xml :as xml]
   [clojure.spec.alpha :as s])
  (:import
   [java.util.regex Pattern]))

(s/fdef xml/value-valid?
  :args (s/cat :valid? ifn? :element xml/element?)
  :ret boolean?)

(s/fdef xml/value-matches?
  :args (s/cat :regex #(instance? Pattern %) :element xml/element?)
  :ret boolean?)

(s/fdef xml/valid-primitive-xml-form
  :args (s/cat :valid? symbol? :constructor symbol? :system-constructor symbol?)
  :ret seq?)

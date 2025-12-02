(ns blaze.terminology-service.local.code-system.core
  (:refer-clojure :exclude [find])
  (:require
   [blaze.fhir.spec.type :as type]))

(defmulti find
  {:arglists '([context url] [context url version])}
  (fn [context url & _]
    (condp = url
      "http://loinc.org" (when (:loinc/context context) :loinc)
      "http://snomed.info/sct" (when (:sct/context context) :sct)
      "urn:ietf:bcp:13" :bcp-13
      "urn:ietf:bcp:47" :bcp-47
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti enhance
  {:arglists '([context code-system])}
  (fn [_ {:keys [url]}]
    (condp = (type/value url)
      "http://loinc.org" :loinc
      "http://snomed.info/sct" :sct
      "urn:ietf:bcp:13" :bcp-13
      "urn:ietf:bcp:47" :bcp-47
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti expand-complete
  {:arglists '([code-system active-only])}
  (fn [{:keys [url]} _]
    (condp = (type/value url)
      "http://loinc.org" :loinc
      "http://snomed.info/sct" :sct
      "urn:ietf:bcp:13" :bcp-13
      "urn:ietf:bcp:47" :bcp-47
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti expand-concept
  {:arglists '([code-system concepts params])}
  (fn [{:keys [url]} _ _]
    (condp = (type/value url)
      "http://loinc.org" :loinc
      "http://snomed.info/sct" :sct
      "urn:ietf:bcp:13" :bcp-13
      "urn:ietf:bcp:47" :bcp-47
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti expand-filter
  {:arglists '([code-system filter params])}
  (fn [{:keys [url]} _ _]
    (condp = (type/value url)
      "http://loinc.org" :loinc
      "http://snomed.info/sct" :sct
      nil)))

(defmulti find-complete
  "Returns the concept according to `params` if it exists in `code-system`."
  {:arglists '([code-system params])}
  (fn [{:keys [url]} _]
    (condp = (type/value url)
      "http://loinc.org" :loinc
      "http://snomed.info/sct" :sct
      "urn:ietf:bcp:13" :bcp-13
      "urn:ietf:bcp:47" :bcp-47
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti find-filter
  {:arglists '([code-system filter params])}
  (fn [{:keys [url]} _ _]
    (condp = (type/value url)
      "http://loinc.org" :loinc
      "http://snomed.info/sct" :sct
      nil)))

(ns blaze.terminology-service.local.code-system.core
  (:refer-clojure :exclude [find])
  (:require
   [blaze.fhir.spec.type :as type]))

(defmulti find
  {:arglists '([context url] [context url version])}
  (fn [{:sct/keys [context]} url & _]
    (condp = (type/value url)
      "http://snomed.info/sct" (when context :sct)
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti enhance
  {:arglists '([context code-system])}
  (fn [_ {:keys [url]}]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti validate-code
  {:arglists '([code-system request])}
  (fn [{:keys [url]} _]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti expand-complete
  {:arglists '([request inactive code-system])}
  (fn [_ _ {:keys [url]}]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      nil)))

(defmulti expand-concept
  {:arglists '([request inactive code-system concepts])}
  (fn [_ _ {:keys [url]} _]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      "http://unitsofmeasure.org" :ucum
      nil)))

(defmulti expand-filter
  {:arglists '([request inactive code-system filter])}
  (fn [_ _ {:keys [url]} _]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      nil)))

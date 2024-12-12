(ns blaze.terminology-service.local.code-system.core
  (:refer-clojure :exclude [find])
  (:require
   [blaze.fhir.spec.type :as type]))

(defmulti find
  {:arglists '([context url] [context url version])}
  (fn [_ url & _]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      nil)))

(defmulti enhance
  {:arglists '([context code-system])}
  (fn [_ {:keys [url]}]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      nil)))

(defmulti validate-code
  {:arglists '([code-system context])}
  (fn [{:keys [url]} _]
    (condp = (type/value url)
      "http://unitsofmeasure.org" :ucum
      "http://snomed.info/sct" :sct
      nil)))

(defmulti expand-complete
  {:arglists '([code-system])}
  (fn [{:keys [url]}]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      nil)))

(defmulti expand-filter
  {:arglists '([code-system filters])}
  (fn [{:keys [url]} _]
    (condp = (type/value url)
      "http://snomed.info/sct" :sct
      nil)))

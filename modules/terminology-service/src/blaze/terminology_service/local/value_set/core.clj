(ns blaze.terminology-service.local.value-set.core
  (:refer-clojure :exclude [find])
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.loinc.context :as lc]
   [clojure.string :as str]))

(defmulti find
  {:arglists '([context url] [context url version])}
  (fn [context url & _]
    (when (:loinc/context context)
      (when (str/starts-with? (type/value url) lc/value-set-prefix)
        :loinc))))

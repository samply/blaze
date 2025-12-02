(ns blaze.terminology-service.local.value-set.loinc
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.loinc.context :as lc]
   [blaze.terminology-service.local.value-set.core :as c]
   [blaze.util :refer [str]]
   [clojure.string :as str]))

(def ^:const ^long value-set-prefix-length (count lc/value-set-prefix))

(defn- build-value-set [id {:keys [title]} value-set-concepts]
  {:fhir/type :fhir/ValueSet
   :name (type/string (str "LOINC_AnswerList_" (str/replace id "-" "_")))
   :title (type/string (format "LOINC AnswerList %s (%s)" id (type/value title)))
   :status #fhir/code "active"
   :copyright lc/copyright
   :compose
   {:fhir/type :fhir.ValueSet/compose
    :include
    [{:fhir/type :fhir.ValueSet.compose/include
      :system #fhir/uri "http://loinc.org"
      :concept
      (mapv
       #(-> (assoc % :fhir/type :fhir.ValueSet.compose.include/concept)
            (dissoc :system))
       (value-set-concepts id))}]}})

(defmethod c/find :loinc
  [{{:keys [value-sets value-set-concepts]} :loinc/context} url & [_version]]
  (ac/completed-future
   (let [id (subs url value-set-prefix-length)]
     (if-let [value-set (value-sets id)]
       (build-value-set id value-set value-set-concepts)
       (ba/not-found (format "The value set `%s` was not found." url))))))

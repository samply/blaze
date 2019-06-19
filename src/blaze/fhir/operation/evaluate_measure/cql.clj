(ns blaze.fhir.operation.evaluate-measure.cql
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.elm.compiler :as compiler]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds])
  (:import
    [java.time OffsetDateTime]))


(defn- evaluate-expression-1
  [context expression-defs]
  (reduce
    (fn [context {:keys [name] :life/keys [expression]}]
      (let [res (compiler/-eval expression context nil)]
        (update context :library-context assoc name res)))
    context
    expression-defs))


(def ^:private patient-cache (volatile! {}))


(defn- list-patients [db]
  (let [key (datomic-util/type-version db "Patient")]
    (if-let [patients (get @patient-cache key)]
      patients
      (-> (vreset! patient-cache {key (datomic-util/list-resources db "Patient")})
          (get key)))))


(s/fdef evaluate-expression
  :args (s/cat :db ::ds/db :now #(instance? OffsetDateTime %)
               :library :life/compiled-library :expression-name string?))

(defn evaluate-expression
  [db now {expression-defs :life/compiled-expression-defs} expression-name]
  (let [context {:now now}]
    (transduce
      (filter
        (fn [patient]
          (-> (assoc context :patient patient)
              (evaluate-expression-1 expression-defs)
              :library-context
              (get expression-name))))
      (fn
        ([sum]
         sum)
        ([count _]
         (inc count)))
      0
      (list-patients db))))



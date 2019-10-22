(ns blaze.fhir.operation.evaluate-measure.measure
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as datomic-util]
    [blaze.datomic.value :as value]
    [blaze.cql-translator :as cql-translator]
    [blaze.elm.compiler :as compiler]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic-spec.core :as ds]
    [prometheus.alpha :as prom])
  (:import
    [java.nio.charset Charset]
    [java.time OffsetDateTime]
    [java.time.temporal Temporal]))


(prom/defhistogram compile-duration-seconds
  "$evaluate-measure compiling latencies in seconds."
  {:namespace "fhir"
   :subsystem "evaluate_measure"}
  (take 12 (iterate #(* 2 %) 0.001)))


(prom/defhistogram evaluate-duration-seconds
  "$evaluate-measure evaluating latencies in seconds."
  {:namespace "fhir"
   :subsystem "evaluate_measure"}
  (take 12 (iterate #(* 2 %) 0.04)))


(def ^:private ^Charset utf-8 (Charset/forName "utf8"))


(defn- extract-cql-code [{:Library/keys [url content]}]
  (if-let [{:Attachment/keys [data]} (first content)]
    (if data
      (String. ^bytes (value/read data) utf-8)
      {::anom/category ::anom/incorrect
       ::anom/message
       (str "Missing embedded data of first attachment in library with "
            "canonical URI `" url "`.")
       :fhir/issue "value"})
    {::anom/category ::anom/incorrect
     ::anom/message
     (str "Missing content in library with canonical URI `" url "`.")
     :fhir/issue "value"}))


(defn- compile-primary-library*
  [db measure]
  (if-let [library-ref (first (:Measure/library measure))]
    (if-let [library (datomic-util/resource-by db :Library/url library-ref)]
      (let [cql-code (extract-cql-code library)]
        (if (::anom/category cql-code)
          cql-code
          (let [library (cql-translator/translate cql-code :locators? true)]
            (if (::anom/category library)
              library
              (compiler/compile-library db library {})))))
      {::anom/category ::anom/incorrect
       ::anom/message
       (str "Can't find the library with canonical URI `" library-ref "`.")
       :fhir/issue "value"
       :fhir.issue/expression "library"})
    {::anom/category ::anom/unsupported
     ::anom/message "Missing primary library. Currently only CQL expressions together with one primary library are supported."
     :fhir/issue "not-supported"
     :measure measure}))


(defn- compile-primary-library
  "Returns the primary library from `measure` in compiled form or an anomaly
  on errors."
  [db measure]
  (with-open [_ (prom/timer compile-duration-seconds)]
    (compile-primary-library* db measure)))


(defn- evaluate-population
  {:arglists '([db now library subject population])}
  [db now library subject
   {{:Expression/keys [language expression]} :Measure.group.population/criteria
    :Measure.group.population/keys [code]}]
  (if (= "text/cql" (:code/code language))
    (cond->
      {:count (cql/evaluate-expression db now library subject expression)}
      code
      (assoc :code (pull/pull-non-primitive db :CodeableConcept code)))
    {::anom/category ::anom/unsupported
     ::anom/message (str "Unsupported language `" language "`.")
     :fhir/issue "not-supported"}))


(defn- evaluate-group
  {:arglists '([db now library subject group])}
  [db now library subject {:Measure.group/keys [code population]}]
  (cond-> {}
    code
    (assoc :code code)
    (seq population)
    (assoc :population (mapv #(evaluate-population db now library subject %) population))))


(defn- evaluate-groups* [db now library subject groups]
  (mapv #(evaluate-group db now library subject %) groups))


(defn- evaluate-groups [db now library subject groups]
  (with-open [_ (prom/timer evaluate-duration-seconds)]
    (evaluate-groups* db now library subject groups)))


(defn- canonical [{:Measure/keys [url version]}]
  (cond-> url version (str "|" version)))


(defn- subject-code
  [{{:CodeableConcept/keys [coding]} :Measure/subjectCodeableConcept}]
  (or (-> coding first :Coding/code :code/code) "Patient"))


(defn- temporal? [x]
  (instance? Temporal x))


(s/fdef evaluate-measure
  :args (s/cat :now #(instance? OffsetDateTime %) :db ::ds/db
               :period (s/tuple temporal? temporal?) :measure ::ds/entity))

(defn evaluate-measure
  "Evaluates `measure` inside `period` in `db` with evaluation time of `now`.

  Returns an already complete MeasureReport which isn't persisted or an anomaly
  in case of errors."
  {:arglists '([now db period measure])}
  [now db [start end] {groups :Measure/group :as measure}]
  (let [library (compile-primary-library db measure)]
    (if (::anom/category library)
      library
      (cond->
        {:resourceType "MeasureReport"
         :status "complete"
         :type "summary"
         :measure (canonical measure)
         :date (pull/to-json now)
         :period
         {:start (pull/to-json start)
          :end (pull/to-json end)}}
        (seq groups)
        (assoc
          :group
          (evaluate-groups db now library (subject-code measure) groups))))))

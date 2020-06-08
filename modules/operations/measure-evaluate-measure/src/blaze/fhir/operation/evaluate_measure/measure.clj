(ns blaze.fhir.operation.evaluate-measure.measure
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.coll.core :as coll]
    [blaze.cql-translator :as cql-translator]
    [blaze.db.api :as d]
    [blaze.elm.compiler :as compiler]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.handler.fhir.util :as fhir-util]
    [cognitect.anomalies :as anom]
    [prometheus.alpha :as prom]
    [taoensso.timbre :as log])
  (:import
    [java.nio.charset Charset]
    [java.time.format DateTimeFormatter]
    [java.util Base64]))


(set! *warn-on-reflection* true)


(prom/defhistogram compile-duration-seconds
  "$evaluate-measure compiling latencies in seconds."
  {:namespace "fhir"
   :subsystem "evaluate_measure"}
  (take 12 (iterate #(* 2 %) 0.001)))


(prom/defhistogram evaluate-duration-seconds
  "$evaluate-measure evaluating latencies in seconds."
  {:namespace "fhir"
   :subsystem "evaluate_measure"}
  (take 22 (iterate #(* 1.4 %) 0.1))
  "subject_type")



;; ---- Compilation -----------------------------------------------------------

(def ^:private ^Charset utf-8 (Charset/forName "utf8"))


(defn- extract-cql-code
  "Extracts the CQL code from the first attachment of `library`.

  Returns an anomaly on errors."
  {:arglists '([library])}
  [{:keys [id content]}]
  (if-let [{:keys [contentType data]} (first content)]
    (if (= "text/cql" contentType)
      (if data
        (String. ^bytes (.decode (Base64/getDecoder) ^String data) utf-8)
        {::anom/category ::anom/incorrect
         ::anom/message (format "Missing embedded data of first attachment in library with id `%s`." id)
         :fhir/issue "value"
         :fhir.issue/expression "Library.content[0].data"})
      {::anom/category ::anom/incorrect
       ::anom/message (format "Non `text/cql` content type of `%s` of first attachment in library with id `%s`." contentType id)
       :fhir/issue "value"
       :fhir.issue/expression "Library.content[0].contentType"})
    {::anom/category ::anom/incorrect
     ::anom/message (format "Missing content in library with id `%s`." id)
     :fhir/issue "value"
     :fhir.issue/expression "Library.content"}))


(defn- compile-library
  "Compiles the CQL code from the first attachment in the `library` resource
  using `node`.

  Returns an anomaly on errors."
  {:arglists '([node library])}
  [node {:keys [id] :as library}]
  (log/debug "Compile library with ID:" id)
  (when-ok [cql-code (extract-cql-code library)]
    (let [library (cql-translator/translate cql-code :locators? true)]
      (case (::anom/category library)
        ::anom/incorrect
        (assoc library
          :fhir/issue "value"
          :fhir.issue/expression "Measure.library")
        (compiler/compile-library node library {})))))


(defn- find-library [db library-ref]
  (coll/first (d/type-query db "Library" [["url" library-ref]])))


(defn- compile-primary-library*
  "Compiles the CQL code from the first library resource which is referenced
  from `measure`.

  The `db` is used to load the library resource and `node` is used for
  compilation.

  Returns an anomaly on errors."
  [node db measure]
  (if-let [library-ref (first (:library measure))]
    (if-let [library (find-library db library-ref)]
      (compile-library node library)
      {::anom/category ::anom/incorrect
       ::anom/message
       (str "Can't find the library with canonical URI `" library-ref "`.")
       :fhir/issue "value"
       :fhir.issue/expression "Measure.library"})
    {::anom/category ::anom/unsupported
     ::anom/message "Missing primary library. Currently only CQL expressions together with one primary library are supported."
     :fhir/issue "not-supported"
     :fhir.issue/expression "Measure.library"
     :measure measure}))


(defn- compile-primary-library
  "Same as `compile-primary-library*` with added metrics collection."
  [node db measure]
  (with-open [_ (prom/timer compile-duration-seconds)]
    (compile-primary-library* node db measure)))



;; ---- Evaluation ------------------------------------------------------------

(defn- evaluate-population
  {:arglists '([db now library subject-type groupIdx populationIdx population])}
  [db now library subject-type groupIdx populationIdx
   {:keys [code] {:keys [language expression]} :criteria}]
  (cond
    (not= "text/cql" language)
    {::anom/category ::anom/unsupported
     ::anom/message (str "Unsupported language `" language "`.")
     :fhir/issue "not-supported"
     :fhir.issue/expression
     (format "Measure.group[%d].population[%d].criteria.language"
             groupIdx populationIdx)}

    (nil? expression)
    {::anom/category ::anom/incorrect
     ::anom/message "Missing expression."
     :fhir/issue "required"
     :fhir.issue/expression
     (format "Measure.group[%d].population[%d].criteria.expression"
             groupIdx populationIdx)}

    :else
    (when-ok [count (cql/evaluate-expression db now library subject-type expression)]
      (cond-> {:count count}
        code
        (assoc :code code)))))


(defn- evaluate-single-stratifier
  {:arglists
   '([db now library subject-type groupIdx populations stratifierIdx stratifier])}
  [db now library subject-type groupIdx populations stratifierIdx
   {:keys [code] {:keys [language expression]} :criteria}]
  (cond
    (not= "text/cql" language)
    {::anom/category ::anom/unsupported
     ::anom/message (str "Unsupported language `" language "`.")
     :fhir/issue "not-supported"
     :fhir.issue/expression
     (format "Measure.group[%d].stratifier[%d].criteria.language"
             groupIdx stratifierIdx)}

    (nil? expression)
    {::anom/category ::anom/incorrect
     ::anom/message "Missing expression."
     :fhir/issue "required"
     :fhir.issue/expression
     (format "Measure.group[%d].stratifier[%d].criteria.expression"
             groupIdx stratifierIdx)}

    :else
    (when-ok [stratums (cql/calc-stratums
                         db now library subject-type
                         (-> populations first :criteria :expression)
                         expression)]
      (cond->
        {:stratum
         (->> stratums
              (mapv
                (fn [[stratum-value count]]
                  [(str (or stratum-value "null")) count]))
              (sort-by first)
              (mapv
                (fn [[stratum-value count]]
                  {:value {:text stratum-value}
                   :population
                   [{:code (-> populations first :code)
                     :count count}]})))}
        code
        (assoc :code [code])))))


(defn- extract-stratifier-component
  "Extracts code and expression-name from `stratifier-component`."
  {:arglists '([groupIdx stratifierIdx componentIdx stratifier-component])}
  [groupIdx stratifierIdx componentIdx
   {:keys [code] {:keys [language expression]} :criteria}]
  (cond
    (nil? code)
    {::anom/category ::anom/incorrect
     ::anom/message "Missing code."
     :fhir/issue "required"
     :fhir.issue/expression
     (format "Measure.group[%d].stratifier[%d].component[%d].code"
             groupIdx stratifierIdx componentIdx)}

    (not= "text/cql" language)
    {::anom/category ::anom/unsupported
     ::anom/message (str "Unsupported language `" language "`.")
     :fhir/issue "not-supported"
     :fhir.issue/expression
     (format "Measure.group[%d].stratifier[%d].component[%d].criteria.language"
             groupIdx stratifierIdx componentIdx)}

    (nil? expression)
    {::anom/category ::anom/incorrect
     ::anom/message "Missing expression."
     :fhir/issue "required"
     :fhir.issue/expression
     (format "Measure.group[%d].stratifier[%d].component[%d].criteria.expression"
             groupIdx stratifierIdx componentIdx)}

    :else [code expression]))


(defn- extract-stratifier-components
  "Extracts code and expression-name from each of `stratifier-components`."
  [groupIdx stratifierIdx stratifier-components]
  (transduce
    (map-indexed vector)
    (completing
      (fn [results [idx component]]
        (let [result (extract-stratifier-component
                       groupIdx stratifierIdx idx component)]
          (if (::anom/category result)
            (reduced result)
            (let [[code expression-name] result]
              (-> results
                  (update :codes conj code)
                  (update :expression-names conj expression-name)))))))
    {:codes []
     :expression-names []}
    stratifier-components))


(defn- evaluate-multi-component-stratifier
  {:arglists
   '([db now library subject-type groupIdx populations stratifierIdx stratifier])}
  [db now library subject-type groupIdx populations stratifierIdx {:keys [component]}]
  (when-ok [results (extract-stratifier-components groupIdx stratifierIdx component)]
    (let [{:keys [codes expression-names]} results]
      (when-ok [stratums (cql/calc-mult-component-stratums
                           db now library subject-type
                           (-> populations first :criteria :expression)
                           expression-names)]
        {:code codes
         :stratum
         (into
           []
           (map
             (fn [[stratum-values count]]
               {:component
                (mapv
                  (fn [code value]
                    {:code code
                     :value {:text (str value)}})
                  codes
                  stratum-values)
                :population
                [{:code (-> populations first :code)
                  :count count}]}))
           stratums)}))))


(defn- evaluate-stratifier
  {:arglists
   '([db now library subject-type groupIdx populations stratifierIdx stratifier])}
  [db now library subject-type groupIdx populations stratifierIdx
   {:keys [component] :as stratifier}]
  (if (seq component)
    (evaluate-multi-component-stratifier
      db now library subject-type groupIdx populations stratifierIdx stratifier)
    (evaluate-single-stratifier
      db now library subject-type groupIdx populations stratifierIdx stratifier)))


(defn- conj-anom
  ([x] x)
  ([res x]
   (if (::anom/category x)
     (reduced x)
     (conj res x))))


(defn- evaluate-populations [db now library subject-type groupIdx populations]
  (transduce
    (map-indexed #(evaluate-population db now library subject-type groupIdx %1 %2))
    conj-anom
    []
    populations))


(defn- evaluate-stratifiers
  [db now library subject-type groupIdx populations stratifiers]
  (transduce
    (map-indexed
      (partial evaluate-stratifier db now library subject-type groupIdx populations))
    conj-anom
    []
    stratifiers))


(defn- evaluate-group
  {:arglists '([db now library subject-type groupIdx group])}
  [db now library subject-type groupIdx
   {:keys [code population stratifier]}]
  (let [evaluated-populations
        (evaluate-populations db now library subject-type groupIdx population)
        evaluated-stratifiers
        (evaluate-stratifiers
          db now library subject-type groupIdx population stratifier)]
    (cond
      (::anom/category evaluated-populations)
      evaluated-populations

      (::anom/category evaluated-stratifiers)
      evaluated-stratifiers

      :else
      (cond-> {}
        code
        (assoc :code code)

        (seq evaluated-populations)
        (assoc :population evaluated-populations)

        (seq evaluated-stratifiers)
        (assoc :stratifier evaluated-stratifiers)))))


(defn- evaluate-groups* [db now library subject-type groups]
  (transduce
    (map-indexed #(evaluate-group db now library subject-type %1 %2))
    conj-anom
    []
    groups))


(defn- evaluate-groups [db now library id subject-type groups]
  (let [timer (prom/timer evaluate-duration-seconds subject-type)]
    (try
      (evaluate-groups* db now library subject-type groups)
      (finally
        (let [duration (prom/observe-duration! timer)]
          (log/debug
            (format "Evaluated Measure with ID `%s` and subject type `%s` in %.0f ms."
                    id subject-type (* duration 1e3))))))))


(defn- canonical [router {:keys [id url version]}]
  (if url
    (cond-> url version (str "|" version))
    (fhir-util/instance-url router "Measure" id)))


(defn- subject-type [{{:keys [coding]} :subjectCodeableConcept}]
  (or (-> coding first :code) "Patient"))

;;TODO: put db, now and library into a context to save args
(defn evaluate-measure
  "Evaluates `measure` inside `period` in `db` with evaluation time of `now`.

  Returns an already completed MeasureReport which isn't persisted or an anomaly
  in case of errors."
  {:arglists '([now db router period measure])}
  [now node db router [start end] {:keys [id] groups :group :as measure}]
  (when-ok [library (compile-primary-library node db measure)]
    (when-ok [evaluated-groups (evaluate-groups db now library id
                                                (subject-type measure) groups)]
      (cond->
        {:resourceType "MeasureReport"
         :status "complete"
         :type "summary"
         :measure (canonical router measure)
         :date (.format DateTimeFormatter/ISO_DATE_TIME now)
         :period
         {:start (str start)
          :end (str end)}}

        (seq evaluated-groups)
        (assoc :group evaluated-groups)))))

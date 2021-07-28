(ns blaze.fhir.operation.evaluate-measure.measure
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.coll.core :as coll]
    [blaze.cql-translator :as cql-translator]
    [blaze.db.api :as d]
    [blaze.elm.compiler.library :as library]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.luid :as luid]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [prometheus.alpha :as prom]
    [taoensso.timbre :as log])
  (:import
    [java.nio.charset StandardCharsets]
    [java.time Clock OffsetDateTime]
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

(defn- extract-cql-code
  "Extracts the CQL code from the first attachment of `library`.

  Returns an anomaly on errors."
  {:arglists '([library])}
  [{:keys [id content]}]
  (if-let [{:keys [contentType data]} (first content)]
    (if (= "text/cql" (type/value contentType))
      (let [data (type/value data)]
        (if data
          (String. ^bytes (.decode (Base64/getDecoder) ^String data)
                   StandardCharsets/UTF_8)
          {::anom/category ::anom/incorrect
           ::anom/message (format "Missing embedded data of first attachment in library with id `%s`." id)
           :fhir/issue "value"
           :fhir.issue/expression "Library.content[0].data"}))
      {::anom/category ::anom/incorrect
       ::anom/message (format "Non `text/cql` content type of `%s` of first attachment in library with id `%s`." contentType id)
       :fhir/issue "value"
       :fhir.issue/expression "Library.content[0].contentType"})
    {::anom/category ::anom/incorrect
     ::anom/message (format "Missing content in library with id `%s`." id)
     :fhir/issue "value"
     :fhir.issue/expression "Library.content"}))


(defn- compile-library*
  "Compiles the CQL code from the first attachment in the `library` resource
  using `node`.

  Returns an anomaly on errors."
  [node library]
  (when-ok [cql-code (extract-cql-code library)]
    (let [library (cql-translator/translate cql-code :locators? true)]
      (case (::anom/category library)
        ::anom/incorrect
        (assoc library
          :fhir/issue "value"
          :fhir.issue/expression "Measure.library")
        (library/compile-library node library {})))))


(defn- compile-library
  "Compiles the CQL code from the first attachment in the `library` resource
  using `node`.

  Returns an anomaly on errors."
  {:arglists '([node library])}
  [node {:keys [id] :as library}]
  (log/debug (format "Start compiling Library with ID `%s`..." id))
  (let [timer (prom/timer compile-duration-seconds)]
    (try
      (compile-library* node library)
      (finally
        (let [duration (prom/observe-duration! timer)]
          (log/debug
            (format "Compiled Library with ID `%s` in %.0f ms."
                    id (* duration 1e3))))))))


(defn- first-library-by-url [db url]
  (coll/first (d/type-query db "Library" [["url" url]])))


(defn- non-deleted-library-handle [db id]
  (when-let [handle (d/resource-handle db "Library" id)]
    (when-not (= (:op handle) :delete) handle)))


(defn- find-library-handle [db library-ref]
  (if-let [handle (first-library-by-url db library-ref)]
    handle
    (non-deleted-library-handle db (peek (str/split library-ref #"/")))))


(defn- find-library [db library-ref]
  (when-let [handle (find-library-handle db library-ref)]
    @(d/pull db handle)))


(defn- compile-primary-library*
  "Compiles the CQL code from the first library resource which is referenced
  from `measure`.

  The `db` is used to load the library resource and `node` is used for
  compilation.

  Returns an anomaly on errors."
  [db measure]
  (if-let [library-ref (-> measure :library first type/value)]
    (if-let [library (find-library db library-ref)]
      (compile-library (d/node db) library)
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
  [db measure]
  (with-open [_ (prom/timer compile-duration-seconds)]
    (compile-primary-library* db measure)))



;; ---- Evaluation ------------------------------------------------------------


(defn- population-tx-ops [{:keys [subject-type]} list-id result]
  [[:create
    {:fhir/type :fhir/List
     :id list-id
     :status #fhir/code"current"
     :mode #fhir/code"working"
     :entry
     (mapv
       (fn [subject-id]
         {:fhir/type :fhir.List/entry
          :item
          (type/map->Reference {:reference (str subject-type "/" subject-id)})})
       result)}]])


(defn- population [{:keys [luids] :as context} fhir-type code result]
  (case (:report-type context)
    "population"
    {:result
     (cond->
       {:fhir/type fhir-type
        :count (int result)}
       code
       (assoc :code code))
     :luids luids}
    "subject-list"
    (let [list-id (first luids)]
      {:result
       (cond->
         {:fhir/type fhir-type
          :count (count result)
          :subjectResults
          (type/map->Reference {:reference (str "List/" list-id)})}
         code
         (assoc :code code))
       :luids (next luids)
       :tx-ops (population-tx-ops context list-id result)})))


(defn- evaluate-population
  {:arglists '([context population-idx population])}
  [{:keys [group-idx] :as context} population-idx
   {:keys [code] {:keys [language expression]} :criteria}]
  (let [language (type/value language)
        expression (type/value expression)]
    (cond
      (not= "text/cql" language)
      {::anom/category ::anom/unsupported
       ::anom/message (str "Unsupported language `" language "`.")
       :fhir/issue "not-supported"
       :fhir.issue/expression
       (format "Measure.group[%d].population[%d].criteria.language"
               group-idx population-idx)}

      (nil? expression)
      {::anom/category ::anom/incorrect
       ::anom/message "Missing expression."
       :fhir/issue "required"
       :fhir.issue/expression
       (format "Measure.group[%d].population[%d].criteria.expression"
               group-idx population-idx)}

      :else
      (when-ok [result (cql/evaluate-expression context expression)]
        (population context :fhir.MeasureReport.group/population code result)))))


(defn- value-concept [value]
  (type/map->CodeableConcept {:text (str (if (nil? value) "null" value))}))


(defn- stratum* [population value]
  {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
   :value (value-concept value)
   :population [population]})


(defn- stratum [context population-code [value result]]
  (-> (population
        context :fhir.MeasureReport.group.stratifier.stratum/population
        population-code result)
      (update :result stratum* value)))


(defn- merge-result*
  "Merges `result` into the return value of the reduction `ret`."
  {:arglists '([ret result])}
  [ret {:keys [result luids tx-ops]}]
  (-> (update ret :result conj result)
      (assoc :luids luids)
      (update :tx-ops into tx-ops)))


(defn- merge-result
  "Merges `result` into the return value of the reduction `ret`.

  Returns a reduced value if `result` is an anomaly."
  [ret result]
  (-> result
      (ba/map (partial merge-result* ret))
      (ba/exceptionally reduced)))


(defn- stratifier* [strata code]
  (cond-> {:fhir/type :fhir.MeasureReport.group/stratifier
           :stratum (sort-by (comp :text :value) strata)}
    code
    (assoc :code [code])))


(defn- stratifier [{:keys [luids] :as context} code population-code strata]
  (-> (reduce
        (fn [{:keys [luids] :as ret} x]
          (->> (stratum (assoc context :luids luids) population-code x)
               (merge-result ret)))
        {:result [] :luids luids :tx-ops []}
        strata)
      (update :result stratifier* code)))


(defn- evaluate-single-stratifier
  {:arglists '([context populations stratifier])}
  [{:keys [group-idx stratifier-idx] :as context} populations
   {:keys [code] {:keys [language expression]} :criteria}]
  (cond
    (not= "text/cql" (type/value language))
    {::anom/category ::anom/unsupported
     ::anom/message (str "Unsupported language `" language "`.")
     :fhir/issue "not-supported"
     :fhir.issue/expression
     (format "Measure.group[%d].stratifier[%d].criteria.language"
             group-idx stratifier-idx)}

    (nil? expression)
    {::anom/category ::anom/incorrect
     ::anom/message "Missing expression."
     :fhir/issue "required"
     :fhir.issue/expression
     (format "Measure.group[%d].stratifier[%d].criteria.expression"
             group-idx stratifier-idx)}

    :else
    (when-ok [strata (cql/calc-strata
                       context
                       (-> populations first :criteria :expression)
                       expression)]
      (stratifier context code (-> populations first :code) strata))))


(defn- missing-code-msg [{:keys [group-idx stratifier-idx component-idx]}]
  (format "Measure.group[%d].stratifier[%d].component[%d].code"
          group-idx stratifier-idx component-idx))


(defn- unsupported-language-msg [{:keys [group-idx stratifier-idx component-idx]}]
  (format "Measure.group[%d].stratifier[%d].component[%d].criteria.language"
          group-idx stratifier-idx component-idx))


(defn- missing-expression [{:keys [group-idx stratifier-idx component-idx]}]
  (format "Measure.group[%d].stratifier[%d].component[%d].criteria.expression"
          group-idx stratifier-idx component-idx))


(defn- extract-stratifier-component
  "Extracts code and expression-name from `stratifier-component`."
  [context {:keys [code] {:keys [language expression]} :criteria}]
  (cond
    (nil? code)
    {::anom/category ::anom/incorrect
     ::anom/message "Missing code."
     :fhir/issue "required"
     :fhir.issue/expression (missing-code-msg context)}

    (not= "text/cql" (type/value language))
    {::anom/category ::anom/unsupported
     ::anom/message (str "Unsupported language `" language "`.")
     :fhir/issue "not-supported"
     :fhir.issue/expression (unsupported-language-msg context)}

    (nil? expression)
    {::anom/category ::anom/incorrect
     ::anom/message "Missing expression."
     :fhir/issue "required"
     :fhir.issue/expression (missing-expression context)}

    :else [code expression]))


(defn- extract-stratifier-component*
  ([_context x] x)
  ([context results [idx component]]
   (if-ok [[code expression-name] (extract-stratifier-component
                                    (assoc context :component-idx idx)
                                    component)]
     (-> (update results :codes conj code)
         (update :expression-names conj expression-name))
     reduced)))


(defn- extract-stratifier-components
  "Extracts code and expression-name from each of `stratifier-components`."
  [context stratifier-components]
  (transduce
    (map-indexed vector)
    (partial extract-stratifier-component* context)
    {:codes []
     :expression-names []}
    stratifier-components))


(defn- multi-component-stratum* [population codes values]
  {:fhir/type :fhir.MeasureReport.group.stratifier/stratum
   :component
   (mapv
     (fn [code value]
       {:fhir/type :fhir.MeasureReport.group.stratifier.stratum/component
        :code code
        :value (value-concept value)})
     codes
     values)
   :population [population]})


(defn- multi-component-stratum [context codes population-code [values result]]
  (-> (population
        context :fhir.MeasureReport.group.stratifier.stratum/population
        population-code result)
      (update :result multi-component-stratum* codes values)))


(defn- multi-component-stratifier* [strata codes]
  {:fhir/type :fhir.MeasureReport.group/stratifier
   :code codes
   :stratum (sort-by (comp #(mapv (comp :text :value) %) :component) strata)})


(defn- multi-component-stratifier
  [{:keys [luids] :as context} codes population-code strata]
  (-> (reduce
        (fn [{:keys [luids] :as ret} x]
          (->> (multi-component-stratum (assoc context :luids luids) codes
                                        population-code x)
               (merge-result ret)))
        {:result [] :luids luids :tx-ops []}
        strata)
      (update :result multi-component-stratifier* codes)))


(defn- evaluate-multi-component-stratifier
  [context populations {:keys [component]}]
  (when-ok [results (extract-stratifier-components context component)]
    (let [{:keys [codes expression-names]} results]
      (when-ok [strata (cql/calc-mult-component-strata
                         context
                         (-> populations first :criteria :expression)
                         expression-names)]
        (multi-component-stratifier context codes (-> populations first :code)
                                    strata)))))


(defn- evaluate-stratifier
  {:arglists '([context populations stratifier])}
  [context populations {:keys [component] :as stratifier}]
  (if (seq component)
    (evaluate-multi-component-stratifier context populations stratifier)
    (evaluate-single-stratifier context populations stratifier)))


(defn- evaluate-populations [{:keys [luids] :as context} populations]
  (transduce
    (map-indexed vector)
    (completing
      (fn [{:keys [luids] :as ret} [idx population]]
        (->> (evaluate-population (assoc context :luids luids) idx population)
             (merge-result ret))))
    {:result [] :luids luids :tx-ops []}
    populations))


(defn- evaluate-stratifiers [{:keys [luids] :as context} populations stratifiers]
  (transduce
    (map-indexed vector)
    (completing
      (fn [{:keys [luids] :as ret} [idx stratifier]]
        (->> (evaluate-stratifier
               (assoc context :luids luids :stratifier-idx idx)
               populations stratifier)
             (merge-result ret))))
    {:result [] :luids luids :tx-ops []}
    stratifiers))


(defn- evaluate-group
  {:arglists '([context group])}
  [context {:keys [code population stratifier]}]
  (when-ok [{:keys [luids] :as evaluated-populations}
            (evaluate-populations context population)
            evaluated-stratifiers
            (evaluate-stratifiers (assoc context :luids luids) population
                                  stratifier)]
    {:result
     (cond-> {:fhir/type :fhir.MeasureReport/group}
       code
       (assoc :code code)

       (seq (:result evaluated-populations))
       (assoc :population (:result evaluated-populations))

       (seq (:result evaluated-stratifiers))
       (assoc :stratifier (:result evaluated-stratifiers)))
     :tx-ops
     (into (:tx-ops evaluated-populations)
           (:tx-ops evaluated-stratifiers))}))


(defn- evaluate-groups* [{:keys [luids] :as context} groups]
  (transduce
    (map-indexed vector)
    (completing
      (fn [{:keys [luids] :as ret} [idx group]]
        (->> (evaluate-group (assoc context :luids luids :group-idx idx) group)
             (merge-result ret))))
    {:result [] :luids luids :tx-ops []}
    groups))


(defn- evaluate-groups-msg [id subject-type duration]
  (format "Evaluated Measure with ID `%s` and subject type `%s` in %.0f ms."
          id subject-type (* duration 1e3)))


(defn- evaluate-groups [{:keys [subject-type] :as context} id groups]
  (log/debug (format "Start evaluating Measure with ID `%s`..." id))
  (let [timer (prom/timer evaluate-duration-seconds subject-type)]
    (when-ok [groups (evaluate-groups* context groups)]
      (let [duration (prom/observe-duration! timer)]
        (log/debug (evaluate-groups-msg id subject-type duration))
        [groups duration]))))


(defn- canonical [base-url router {:keys [id url version]}]
  (if-let [url (type/value url)]
    (cond-> url version (str "|" version))
    (fhir-util/instance-url base-url router "Measure" id)))


(defn- get-first-code [codings system]
  (some
    #(when (= system (-> % :system type/value))
       (-> % :code type/value))
    codings))


(defn- subject-type [{{codings :coding} :subject}]
  (or (get-first-code codings "http://hl7.org/fhir/resource-types") "Patient"))


(defn eval-duration [duration]
  (type/map->Extension
    {:url "https://samply.github.io/blaze/fhir/StructureDefinition/eval-duration"
     :value
     (type/map->Quantity
       {:code #fhir/code"s"
        :system #fhir/uri"http://unitsofmeasure.org"
        :unit "s"
        :value (bigdec duration)})}))


(defn- measure-report [report-type measure-ref now start end result duration]
  (cond->
    {:fhir/type :fhir/MeasureReport
     :extension [(eval-duration duration)]
     :status #fhir/code"complete"
     :type
     (if (= "subject-list" report-type)
       #fhir/code"subject-list"
       #fhir/code"summary")
     :measure (type/->Canonical measure-ref)
     :date now
     :period
     (type/map->Period
       {:start (type/->DateTime (str start))
        :end (type/->DateTime (str end))})}

    (seq (:result result))
    (assoc :group (:result result))))


(defn- now [clock]
  (OffsetDateTime/now ^Clock clock))


(defn- successive-luids [{:keys [clock rng-fn]}]
  (luid/successive-luids clock (rng-fn)))


(defn evaluate-measure
  "Evaluates `measure` inside `period` in `db` with evaluation time of `now`.

  Returns an already completed MeasureReport which isn't persisted or an anomaly
  in case of errors."
  {:arglists '([context db base-url router measure params])}
  [{:keys [clock] :as context} db base-url router
   {:keys [id] groups :group :as measure}
   {:keys [report-type] [start end] :period}]
  (when-ok [library (compile-primary-library db measure)]
    (let [now (now clock)
          context (assoc context
                    :db db :now now :library library
                    :subject-type (subject-type measure)
                    :report-type report-type
                    :luids (successive-luids context))]
      (when-ok [[groups duration] (evaluate-groups context id groups)]
        (cond->
          {:resource
           (measure-report report-type (canonical base-url router measure) now
                           start end groups duration)}
          (seq (:tx-ops groups))
          (assoc :tx-ops (:tx-ops groups)))))))

(ns blaze.fhir.operation.evaluate-measure.measure
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.cql-translator :as cql-translator]
   [blaze.db.api :as d]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler-spec]
   [blaze.elm.compiler.external-data :as ed]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.expression :as-alias expr]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.measure.population :as population]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier :as stratifier]
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.luid :as luid]
   [clojure.string :as str]
   [java-time.api :as time]
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
          (ba/incorrect
           (format "Missing embedded data of first attachment in library with id `%s`." id)
           :fhir/issue "value"
           :fhir.issue/expression "Library.content[0].data")))
      (ba/incorrect
       (format "Non `text/cql` content type of `%s` of first attachment in library with id `%s`." contentType id)
       :fhir/issue "value"
       :fhir.issue/expression "Library.content[0].contentType"))
    (ba/incorrect
     (format "Missing content in library with id `%s`." id)
     :fhir/issue "value"
     :fhir.issue/expression "Library.content")))

(defn- translate [cql-code]
  (-> (cql-translator/translate cql-code)
      (ba/exceptionally
       #(assoc %
               :fhir/issue "value"
               :fhir.issue/expression "Measure.library"))))

(defn- compile-library*
  "Compiles the CQL code from the first attachment in the `library` resource
  using `node`.

  Returns an anomaly on errors."
  [node library opts]
  (when-ok [cql-code (extract-cql-code library)
            library (translate cql-code)]
    (library/compile-library node library opts)))

(defn- compile-library
  "Compiles the CQL code from the first attachment in the `library` resource
  using `node`.

  Returns an anomaly on errors."
  {:arglists '([node library opts])}
  [node {:keys [id] :as library} opts]
  (log/debug (format "Start compiling Library with ID `%s`..." id))
  (let [timer (prom/timer compile-duration-seconds)]
    (try
      (compile-library* node library opts)
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

(defn- compile-primary-library
  "Compiles the CQL code from the first library resource which is referenced
  from `measure`.

  The `db` is used to load the library resource and `node` is used for
  compilation.

  Returns an anomaly on errors."
  [db measure opts]
  (if-let [library-ref (-> measure :library first type/value)]
    (if-let [library (find-library db library-ref)]
      (compile-library (d/node db) library opts)
      (ba/incorrect
       (format "The Library resource with canonical URI `%s` was not found." library-ref)
       :fhir/issue "value"
       :fhir.issue/expression "Measure.library"))
    (ba/unsupported
     "Missing primary library. Currently only CQL expressions together with one primary library are supported."
     :fhir/issue "not-supported"
     :fhir.issue/expression "Measure.library"
     :measure measure)))

;; ---- Evaluation ------------------------------------------------------------

(defn- evaluate-population-futures [context populations]
  (into [] (map-indexed (partial population/evaluate context)) populations))

(defn- evaluate-populations [context populations]
  (let [futures (evaluate-population-futures context populations)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) (population/reduce-op context) futures))))

(defn- evaluate-stratifiers
  [{:keys [luids] :as context} evaluated-populations stratifiers]
  (transduce
   (map-indexed vector)
   (completing
    (fn [ret [idx stratifier]]
      (ac/then-compose
       ret
       (fn [{:keys [luids] :as ret}]
         (-> (stratifier/evaluate
              (assoc context :luids luids :stratifier-idx idx)
              evaluated-populations stratifier)
             (ac/then-apply (partial u/merge-result ret)))))))
   (ac/completed-future {:result [] :luids luids :tx-ops []})
   stratifiers))

(defn- population-basis [{:keys [extension]}]
  (some
   (fn [{:keys [url value]}]
     (when (= "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis" url)
       (let [basis (type/value value)]
         (cond-> basis (= "boolean" basis) keyword))))
   extension))

(defn- evaluate-group
  {:arglists '([context group])}
  [{:keys [report-type] :as context}
   {:keys [code population stratifier] :as group}]
  (let [context (assoc context
                       :population-basis (population-basis group)
                       :return-handles? (or (= "subject-list" report-type)
                                            (some? (seq stratifier))))]
    (-> (evaluate-populations context population)
        (ac/then-compose
         (fn [{:keys [luids] :as evaluated-populations}]
           (-> (evaluate-stratifiers (assoc context :luids luids)
                                     evaluated-populations
                                     stratifier)
               (ac/then-apply
                (fn [{:keys [luids] :as evaluated-stratifiers}]
                  {:result
                   (cond-> {:fhir/type :fhir.MeasureReport/group}
                     code
                     (assoc :code code)

                     (seq (:result evaluated-populations))
                     (assoc :population (:result evaluated-populations))

                     (seq (:result evaluated-stratifiers))
                     (assoc :stratifier (:result evaluated-stratifiers)))
                   :luids luids
                   :tx-ops
                   (into (:tx-ops evaluated-populations)
                         (:tx-ops evaluated-stratifiers))}))))))))

(defn- evaluate-groups* [{:keys [luids] :as context} groups]
  (transduce
   (map-indexed vector)
   (completing
    (fn [ret [idx group]]
      (ac/then-compose
       ret
       (fn [{:keys [luids] :as ret}]
         (-> (evaluate-group (assoc context :luids luids :group-idx idx) group)
             (ac/then-apply (partial u/merge-result ret)))))))
   (ac/completed-future {:result [] :luids luids :tx-ops []})
   groups))

(defn- evaluate-groups-msg [id subject-type duration]
  (format "Evaluated Measure with ID `%s` and subject type `%s` in %.0f ms."
          id subject-type (* duration 1e3)))

(defn- evaluate-groups
  [{:keys [subject-type] :as context} {:keys [id] groups :group}]
  (log/debug (format "Start evaluating Measure with ID `%s`..." id))
  (let [timer (prom/timer evaluate-duration-seconds subject-type)]
    (do-sync [groups (evaluate-groups* context groups)]
      (let [duration (prom/observe-duration! timer)]
        (log/debug (evaluate-groups-msg id subject-type duration))
        [groups duration]))))

(defn- canonical [context {:keys [id url version]}]
  (if-let [url (type/value url)]
    (cond-> url version (str "|" version))
    (fhir-util/instance-url context "Measure" id)))

(defn- get-first-code [codings system]
  (some
   #(when (= system (-> % :system type/value))
      (-> % :code type/value))
   codings))

(defn- subject-type [{{codings :coding} :subject}]
  (or (get-first-code codings "http://hl7.org/fhir/resource-types") "Patient"))

(defn- eval-duration [duration]
  (type/extension
   {:url "https://samply.github.io/blaze/fhir/StructureDefinition/eval-duration"
    :value
    (type/map->Quantity
     {:code #fhir/code"s"
      :system #fhir/uri"http://unitsofmeasure.org"
      :unit #fhir/string"s"
      :value (bigdec duration)})}))

(defn- local-ref [handle]
  (str (name (fhir-spec/fhir-type handle)) "/" (:id handle)))

(defn- measure-report
  [{:keys [now report-type subject-handle] :as context} measure
   {[start end] :period} [{:keys [result]} duration]]
  (cond->
   {:fhir/type :fhir/MeasureReport
    :extension [(eval-duration duration)]
    :status #fhir/code"complete"
    :type
    (case report-type
      "population" #fhir/code"summary"
      "subject-list" #fhir/code"subject-list"
      "subject" #fhir/code"individual")
    :measure (type/canonical (canonical context measure))
    :date now
    :period
    (type/map->Period
     {:start (type/dateTime (str start))
      :end (type/dateTime (str end))})}

    subject-handle
    (assoc :subject (type/map->Reference {:reference (local-ref subject-handle)}))

    (seq result)
    (assoc :group result)))

(defn- now [clock]
  (OffsetDateTime/now ^Clock clock))

(defn- successive-luids [{:keys [clock rng-fn]}]
  (luid/successive-luids clock (rng-fn)))

(defn- missing-subject-msg [type id]
  (format "Subject with type `%s` and id `%s` was not found." type id))

(defn- subject-handle* [db type id]
  (if-let [{:keys [op] :as handle} (d/resource-handle db type id)]
    (if (identical? :delete op)
      (ba/incorrect (missing-subject-msg type id))
      (ed/mk-resource db handle))
    (ba/incorrect (missing-subject-msg type id))))

(defn- type-mismatch-msg [measure-subject-type eval-subject-type]
  (format "Type mismatch between evaluation subject `%s` and Measure subject `%s`."
          eval-subject-type measure-subject-type))

(defn- subject-handle [db subject-type subject-ref]
  (if (vector? subject-ref)
    (if (= subject-type (first subject-ref))
      (subject-handle* db subject-type (second subject-ref))
      (ba/incorrect (type-mismatch-msg subject-type (first subject-ref))))
    (subject-handle* db subject-type subject-ref)))

(defn- attach-cache* [cache [name expr]]
  [name (update expr :expression c/attach-cache cache)])

(defn- attach-cache [expression-defs cache]
  (into {} (map (partial attach-cache* cache)) expression-defs))

(defn- eval-unfiltered-xf [context]
  (comp (filter (comp #{"Unfiltered"} :context val))
        (map
         (fn [[name {expr :expression :as expression-def}]]
           (when-ok [expr (cql/evaluate-expression-1 context nil name expr)]
             [name (assoc expression-def :expression expr)])))
        (halt-when ba/anomaly?)))

(defn- eval-unfiltered [context expression-defs]
  (transduce (eval-unfiltered-xf context)
             (completing (fn [r [k v]] (assoc r k v)))
             expression-defs expression-defs))

(defn- enhance-context
  [{:keys [clock db timeout]
    ::expr/keys [cache]
    :or {timeout (time/hours 1)}
    :as context} measure
   {:keys [report-type subject-ref]}]
  (let [subject-type (subject-type measure)
        now (now clock)
        timeout-instant (time/instant (time/plus now timeout))]
    (when-ok [{:keys [expression-defs function-defs parameter-default-values]}
              (compile-primary-library db measure {})
              subject-handle (some->> subject-ref (subject-handle db subject-type))]
      (let [context
            (assoc context
                   :now now
                   :timeout-eclipsed? #(not (.isBefore (.instant ^Clock clock) timeout-instant))
                   :timeout timeout
                   :expression-defs expression-defs
                   :function-defs function-defs
                   :subject-type subject-type
                   :report-type report-type
                   :luids (successive-luids context))]
        (when-ok [expression-defs (eval-unfiltered context expression-defs)]
          (cond-> (assoc context
                         :expression-defs
                         (cond-> (-> (library/resolve-all-refs expression-defs)
                                     (library/resolve-param-refs parameter-default-values))
                           cache (attach-cache cache)))
            subject-handle
            (assoc :subject-handle subject-handle)))))))

(defn evaluate-measure
  "Evaluates `measure` inside `period` in `db` with evaluation time of `now`.

  Returns a CompletableFuture that will complete with an already completed
  MeasureReport under :resource which isn't persisted and optional :tx-ops or
  will complete exceptionally with an anomaly in case of errors."
  {:arglists '([context measure params])}
  [context {:keys [id] :as measure} params]
  (if-ok [context (enhance-context context measure params)]
    (-> (evaluate-groups context measure)
        (ac/exceptionally #(assoc % :measure-id id))
        (ac/then-apply
         (fn [[{:keys [tx-ops]} :as result]]
           (cond->
            {:resource (measure-report context measure params result)}
             (seq tx-ops)
             (assoc :tx-ops tx-ops)))))
    #(ac/completed-future (assoc % :measure-id id))))

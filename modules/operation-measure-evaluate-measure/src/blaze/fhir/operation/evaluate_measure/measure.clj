(ns blaze.fhir.operation.evaluate-measure.measure
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.cql.translator :as cql-translator]
   [blaze.db.api :as d]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.expression :as-alias expr]
   [blaze.elm.resource :as cr]
   [blaze.fhir.operation.evaluate-measure.measure.group :as group]
   [blaze.fhir.operation.evaluate-measure.measure.population :as pop]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier :as strat]
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.luid :as luid]
   [blaze.module :as m]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]
   [prometheus.alpha :as prom]
   [taoensso.timbre :as log])
  (:import
   [java.nio.charset StandardCharsets]
   [java.time Clock Duration]
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
  (take 16 (iterate #(* 2 %) 0.1))
  "subject_type")

;; ---- Compilation -----------------------------------------------------------

(def ^:private text-cql-content
  #(when (-> % :contentType type/value #{"text/cql"}) %))

(defn- extract-cql-code
  "Extracts the CQL code from the first attachment of `library`.

  Returns an anomaly on errors."
  {:arglists '([library])}
  [{:keys [id content]}]
  (if-let [{:keys [data]} (some text-cql-content content)]
    (let [data (type/value data)]
      (if data
        (String. ^bytes (.decode (Base64/getDecoder) ^String data)
                 StandardCharsets/UTF_8)
        (ba/incorrect
         (format "Missing embedded data of first attachment in library with id `%s`." id)
         :fhir/issue "value"
         :fhir.issue/expression "Library.content[0].data")))
    (ba/incorrect
     (format "No attachment with `text/cql` content type found in library with id `%s`." id)
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
  using `context`.

  Returns an anomaly on errors."
  [context library opts]
  (when-ok [cql-code (extract-cql-code library)
            library (translate cql-code)]
    (library/compile-library context library opts)))

(defn- compile-library
  "Compiles the CQL code from the first attachment in the `library` resource
  using `context`.

  Returns an anomaly on errors."
  {:arglists '([context library opts])}
  [context {:keys [id] :as library} opts]
  (log/debug (format "Start compiling Library with ID `%s`..." id))
  (let [timer (prom/timer compile-duration-seconds)]
    (try
      (compile-library* context library opts)
      (finally
        (let [duration (prom/observe-duration! timer)]
          (log/debug
           (format "Compiled Library with ID `%s` in %.0f ms."
                   id (* duration 1e3))))))))

(defn- first-library-by-url [db url]
  (coll/first (d/type-query db "Library" [["url" url]])))

(defn- non-deleted-library-handle [db id]
  (when-let [handle (d/resource-handle db "Library" id)]
    (when-not (d/deleted? handle)
      handle)))

(defn- find-library-handle [db library-ref]
  (if-let [handle (first-library-by-url db library-ref)]
    handle
    (let [literal-ref (s/conform :blaze.fhir/literal-ref library-ref)]
      (when-not (s/invalid? literal-ref)
        (let [[type id] literal-ref]
          (when (= "Library" type)
            (non-deleted-library-handle db id)))))))

(defn- find-library [db library-ref]
  (if-let [handle (find-library-handle db library-ref)]
    (d/pull db handle)
    (ac/completed-future
     (ba/incorrect
      (format "The Library resource with canonical URI `%s` was not found." library-ref)
      :fhir/issue "value"
      :fhir.issue/expression "Measure.library"))))

(defn- remove-unused-defs
  "Removes all expression definitions from `expression-defs` that are not
  referenced by `measure`.

  This function should be only called after `library/resolve-all-refs` was
  called in order to ensure that no open references exist."
  [expression-defs measure]
  (into {} (filter (comp (u/expression-names measure) key)) expression-defs))

(defn- compile-primary-library
  "Compiles the CQL code from the first library resource which is referenced
  from `measure`.

  The `db` is used to load the library resource and `node` is used for
  compilation.

  Returns an anomaly on errors."
  [db terminology-service measure opts]
  (if-let [library-ref (-> measure :library first type/value)]
    (do-sync [library (find-library db library-ref)]
      (let [context (cond-> {:node (d/node db)}
                      terminology-service
                      (assoc :terminology-service terminology-service))]
        (compile-library context library opts)))
    (ac/completed-future
     (ba/unsupported
      "Missing primary library. Currently only CQL expressions together with one primary library are supported."
      :fhir/issue "not-supported"
      :fhir.issue/expression "Measure.library"
      :measure measure))))

;; ---- Evaluation ------------------------------------------------------------

(defn- evaluate-population-futures [context populations]
  (into [] (map-indexed (partial pop/evaluate context)) populations))

(defn- evaluate-group*
  [{:group/keys [combine-op] :as context} {:keys [population]}]
  (let [futures (evaluate-population-futures context population)]
    (do-sync [_ (ac/all-of futures)]
      (transduce (map ac/join) combine-op futures))))

(defn- stratifiers-reduce-op [context stratifiers]
  (when-ok [reduce-ops
            (into
             []
             (comp (map-indexed
                    (fn [idx stratifier]
                      (strat/reduce-op
                       (assoc context :stratifier-idx idx)
                       stratifier)))
                   (halt-when ba/anomaly?))
             stratifiers)]
    (fn [db]
      (let [reduce-ops (mapv #(% db) reduce-ops)]
        (fn
          ([] (mapv #(%) reduce-ops))
          ([rets x]
           (loop [[reduce-op & reduce-ops] reduce-ops
                  [ret & rets] rets
                  result []]
             (if reduce-op
               (let [ret (reduce-op ret x)]
                 (if (reduced? ret)
                   ret
                   (recur reduce-ops rets (conj result ret))))
               result))))))))

(defn- population-basis [{:keys [extension]}]
  (some
   (fn [{:keys [url value]}]
     (when (= "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis" url)
       (let [basis (type/value value)]
         (when-not (= "boolean" basis)
           basis))))
   extension))

(defn- assoc-ops
  [{:keys [report-type] luid-generator ::luid/generator :as context}
   {:keys [code stratifier]}]
  (if (= "subject-list" report-type)
    (if (seq stratifier)
      (when-ok [reduce-op (stratifiers-reduce-op context stratifier)]
        (assoc
         context
         :reduce-op
         (fn [db]
           (let [reduce-op (reduce-op db)]
             (fn
               ([] {:handles [] :strata (reduce-op)})
               ([ret] ret)
               ([{:keys [handles strata]} handle]
                (let [strata (reduce-op strata handle)]
                  (if (reduced? strata)
                    strata
                    {:handles (conj handles handle)
                     :strata strata}))))))
         :combine-op
         (fn
           ([]
            {:handles []
             :strata (mapv (constantly {}) stratifier)})
           ([{handles-a :handles strata-a :strata}
             {handles-b :handles strata-b :strata}]
            {:handles (into handles-a handles-b)
             :strata (mapv (partial merge-with into) strata-a strata-b)}))
         :group/combine-op
         (group/combine-op-subject-list-stratifier luid-generator code stratifier)))
      (assoc
       context
       :reduce-op (fn [_db] conj)
       :combine-op into
       :group/combine-op (group/combine-op-subject-list luid-generator code)))
    (if (seq stratifier)
      (when-ok [reduce-op (stratifiers-reduce-op context stratifier)]
        (assoc
         context
         :reduce-op
         (fn [db]
           (let [reduce-op (reduce-op db)]
             (fn
               ([] {:count 0 :strata (reduce-op)})
               ([ret] ret)
               ([{:keys [count strata]} handles]
                (let [strata (reduce-op strata handles)]
                  (if (reduced? strata)
                    strata
                    {:count (inc count)
                     :strata strata}))))))
         :combine-op
         (fn
           ([]
            {:count 0
             :strata (mapv (constantly {}) stratifier)})
           ([{count-a :count strata-a :strata} {count-b :count strata-b :strata}]
            {:count (+ count-a count-b)
             :strata (mapv (partial merge-with +) strata-a strata-b)}))
         :group/combine-op
         (group/combine-op-count-stratifier luid-generator code stratifier)))
      (assoc
       context
       :reduce-op (fn [_db] ((map (constantly 1)) +))
       :combine-op +
       :group/combine-op (group/combine-op-count luid-generator code)))))

(defn- evaluate-group [context group]
  (if-ok [context (-> (assoc context :population-basis (population-basis group))
                      (assoc-ops group))]
    (evaluate-group* context group)
    ac/completed-future))

(defn- evaluate-groups* [{::luid/keys [generator] :as context} groups]
  (transduce
   (map-indexed vector)
   (completing
    (fn [ret [idx group]]
      (ac/then-compose
       ret
       (fn [{::luid/keys [generator] :as ret}]
         (-> (evaluate-group (assoc context ::luid/generator generator :group-idx idx) group)
             (ac/then-apply (partial u/merge-result ret)))))))
   (ac/completed-future {:result [] ::luid/generator generator :tx-ops []})
   groups))

(defn- cancelled-groups-msg [id subject-type duration]
  (format "Evaluation of Measure with ID `%s` and subject type `%s` was cancelled after %.0f ms."
          id subject-type (* duration 1e3)))

(defn- evaluate-groups-msg [id subject-type duration]
  (format "Evaluated Measure with ID `%s` and subject type `%s` in %.0f ms."
          id subject-type (* duration 1e3)))

(defn- evaluate-groups
  [{:keys [subject-type] :as context} {:keys [id] groups :group}]
  (log/debug (format "Start evaluating Measure with ID `%s`..." id))
  (let [timer (prom/timer evaluate-duration-seconds subject-type)]
    (-> (evaluate-groups* context groups)
        (ac/handle
         (fn [groups anom]
           (let [duration (prom/observe-duration! timer)]
             (if anom
               (do (when (ba/interrupted? anom)
                     (log/debug (cancelled-groups-msg id subject-type duration)))
                   anom)
               (do (log/debug (evaluate-groups-msg id subject-type duration))
                   [groups duration]))))))))

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
    (type/quantity
     {:code #fhir/code "s"
      :system #fhir/uri "http://unitsofmeasure.org"
      :unit #fhir/string "s"
      :value (type/decimal (bigdec duration))})}))

(defn- bloom-filter-ratio
  "Creates an extension with a number of available Bloom filters over the total
  number of requested Bloom filters."
  [bloom-filters]
  (type/extension
   {:url "https://samply.github.io/blaze/fhir/StructureDefinition/bloom-filter-ratio"
    :value
    (type/ratio
     {:numerator
      (type/quantity {:value (type/decimal (bigdec (count (coll/eduction (remove ba/anomaly?) bloom-filters))))})
      :denominator
      (type/quantity {:value (type/decimal (bigdec (count bloom-filters)))})})}))

(defn- local-ref [handle]
  (str (name (:fhir/type handle)) "/" (:id handle)))

(defn- measure-report
  [{:keys [now report-type subject-handle bloom-filters] :as context} measure
   {[start end] :period} [{:keys [result]} duration]]
  (cond->
   {:fhir/type :fhir/MeasureReport
    :extension
    [(eval-duration duration)
     (bloom-filter-ratio bloom-filters)]
    :status #fhir/code "complete"
    :type
    (case report-type
      "population" #fhir/code "summary"
      "subject-list" #fhir/code "subject-list"
      "subject" #fhir/code "individual")
    :measure (type/canonical (canonical context measure))
    :date now
    :period
    (type/period
     {:start (type/dateTime (str start))
      :end (type/dateTime (str end))})}

    subject-handle
    (assoc :subject (type/reference {:reference (type/string (local-ref subject-handle))}))

    (seq result)
    (assoc :group result)))

(defn- missing-subject-msg [type id]
  (format "Subject with type `%s` and id `%s` was not found." type id))

(defn- subject-handle* [db type id]
  (if-let [handle (d/resource-handle db type id)]
    (if (d/deleted? handle)
      (ba/incorrect (missing-subject-msg type id))
      handle)
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

(defn- timeout-eclipsed-msg [timeout]
  (format "Timeout of %d millis eclipsed while evaluating."
          (.toMillis ^Duration timeout)))

(defn- attach-cache* [cache context [name {:keys [expression] :as expr}]]
  (let [[expression bloom-filters] (c/attach-cache expression cache)]
    (-> (assoc-in context [:expression-defs name] (assoc expr :expression expression))
        (update :bloom-filters into bloom-filters))))

(defn- attach-cache [{:keys [expression-defs] :as context} cache]
  (reduce
   (partial attach-cache* cache)
   (assoc context :bloom-filters [])
   expression-defs))

(defn timeout-eclipsed-fn [clock now timeout]
  (let [timeout-instant (time/instant (time/plus now timeout))]
    #(when-not (.isBefore (.instant ^Clock clock) timeout-instant)
       (ba/interrupted
        (timeout-eclipsed-msg timeout)
        :timeout timeout))))

(defn- enhance-context
  [{:keys [clock db timeout terminology-service]
    :blaze/keys [cancelled?]
    ::expr/keys [cache]
    :or {timeout (time/hours 1)}
    :as context} measure
   {:keys [report-type subject-ref]}]
  (let [subject-type (subject-type measure)
        now (time/offset-date-time clock)
        timeout-eclipsed? (timeout-eclipsed-fn clock now timeout)]
    (do-sync [{:keys [expression-defs function-defs parameter-default-values]}
              (compile-primary-library db terminology-service measure {})]
      (when-ok [subject-handle (some->> subject-ref (subject-handle db subject-type))]
        (let [optimize (partial library/optimize db)
              context
              (cond->
               (assoc
                context
                :db db
                :now now
                ;; if a cancelled? function is available we don't use timeout
                :interrupted? (if cancelled? cancelled? timeout-eclipsed?)
                :expression-defs expression-defs
                :parameters parameter-default-values
                :subject-type subject-type
                :report-type report-type
                ::luid/generator (m/luid-generator context))
                function-defs
                (assoc :function-defs function-defs))]
          (when-ok [expression-defs (library/eval-unfiltered context expression-defs)
                    expression-defs (library/resolve-all-refs expression-defs)]
            (cond-> (assoc
                     context
                     :expression-defs
                     (-> (remove-unused-defs expression-defs measure)
                         (library/resolve-params parameter-default-values)
                         (optimize)))
              cache (attach-cache cache)
              subject-handle
              (assoc :subject-handle (cr/mk-resource db subject-handle)))))))))

(defn evaluate-measure
  "Evaluates `measure` inside `context` with `params`.

  Returns a CompletableFuture that will complete with an already completed
  MeasureReport under :resource which isn't persisted and optional :tx-ops or
  will complete exceptionally with an anomaly in case of errors."
  {:arglists '([context measure params])}
  [context {:keys [id] :as measure} params]
  (-> (enhance-context context measure params)
      (ac/then-compose
       (fn [context]
         (do-sync [[{:keys [tx-ops]} :as result] (evaluate-groups context measure)]
           (cond->
            {:resource (measure-report context measure params result)}
             (seq tx-ops)
             (assoc :tx-ops tx-ops)))))
      (ac/exceptionally #(assoc % :measure-id id))))

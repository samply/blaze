(ns blaze.fhir.operation.evaluate-measure.measure
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.coll.core :as coll]
    [blaze.cql-translator :as cql-translator]
    [blaze.db.api :as d]
    [blaze.elm.compiler.library :as library]
    [blaze.fhir.operation.evaluate-measure.measure.population :as population]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier :as stratifier]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.luid :as luid]
    [clojure.string :as str]
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
  (-> (cql-translator/translate cql-code :locators? true)
      (ba/exceptionally
        #(assoc %
           :fhir/issue "value"
           :fhir.issue/expression "Measure.library"))))


(defn- compile-library*
  "Compiles the CQL code from the first attachment in the `library` resource
  using `node`.

  Returns an anomaly on errors."
  [node library]
  (when-ok [cql-code (extract-cql-code library)
            library (translate cql-code)]
    (library/compile-library node library {})))


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


(defn- compile-primary-library
  "Compiles the CQL code from the first library resource which is referenced
  from `measure`.

  The `db` is used to load the library resource and `node` is used for
  compilation.

  Returns an anomaly on errors."
  [db measure]
  (if-let [library-ref (-> measure :library first type/value)]
    (if-let [library (find-library db library-ref)]
      (compile-library (d/node db) library)
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


(defn- evaluate-populations [{:keys [luids] :as context} populations]
  (transduce
    (map-indexed vector)
    (completing
      (fn [{:keys [luids] :as ret} [idx population]]
        (->> (population/evaluate (assoc context :luids luids) idx population)
             (u/merge-result ret))))
    {:result [] :luids luids :tx-ops []}
    populations))


(defn- evaluate-stratifiers [{:keys [luids] :as context} populations stratifiers]
  (transduce
    (map-indexed vector)
    (completing
      (fn [{:keys [luids] :as ret} [idx stratifier]]
        (->> (stratifier/evaluate
               (assoc context :luids luids :stratifier-idx idx)
               populations stratifier)
             (u/merge-result ret))))
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
             (u/merge-result ret))))
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


(defn eval-duration [duration]
  (type/extension
    {:url "https://samply.github.io/blaze/fhir/StructureDefinition/eval-duration"
     :value
     (type/map->Quantity
       {:code #fhir/code"s"
        :system #fhir/uri"http://unitsofmeasure.org"
        :unit "s"
        :value (bigdec duration)})}))


(defn- local-ref [handle]
  (str (name (fhir-spec/fhir-type handle)) "/" (:id handle)))


(defn- measure-report
  [report-type subject-handle measure-ref now start end result duration]
  (cond->
    {:fhir/type :fhir/MeasureReport
     :extension [(eval-duration duration)]
     :status #fhir/code"complete"
     :type
     (case report-type
       "population" #fhir/code"summary"
       "subject-list" #fhir/code"subject-list"
       "subject" #fhir/code"individual")
     :measure (type/canonical measure-ref)
     :date now
     :period
     (type/map->Period
       {:start (type/->DateTime (str start))
        :end (type/->DateTime (str end))})}

    subject-handle
    (assoc :subject (type/map->Reference {:reference (local-ref subject-handle)}))

    (seq (:result result))
    (assoc :group (:result result))))


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


(defn evaluate-measure
  "Evaluates `measure` inside `period` in `db` with evaluation time of `now`.

  Returns an already completed MeasureReport under :resource which isn't
  persisted and optional :tx-ops or an anomaly in case of errors."
  {:arglists '([context measure params])}
  [{:keys [clock db] :as context}
   {:keys [id] groups :group :as measure}
   {:keys [report-type subject-ref] [start end] :period}]
  (when-ok [library (compile-primary-library db measure)
            now (now clock)
            subject-type (subject-type measure)
            subject-handle (some->> subject-ref (subject-handle db subject-type))
            context (cond->
                      (assoc context
                        :db db :now now :library library
                        :subject-type subject-type
                        :report-type report-type
                        :luids (successive-luids context))
                      subject-handle
                      (assoc :subject-handle subject-handle))
            [groups duration] (evaluate-groups context id groups)]
    (cond->
      {:resource
       (measure-report report-type subject-handle (canonical context measure)
                       now start end groups duration)}
      (seq (:tx-ops groups))
      (assoc :tx-ops (:tx-ops groups)))))

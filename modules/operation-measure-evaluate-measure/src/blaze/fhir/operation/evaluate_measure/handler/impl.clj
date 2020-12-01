(ns blaze.fhir.operation.evaluate-measure.handler.impl
  (:require
    [blaze.async.comp :as ac]
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.fhir.operation.evaluate-measure.measure :refer [evaluate-measure]]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.luid :refer [luid]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [reitit.core :as reitit]
    [ring.util.response :as ring])
  (:import
    [java.time Clock OffsetDateTime]))


(set! *warn-on-reflection* true)


(defn- now [clock]
  (OffsetDateTime/now ^Clock clock))


(defn- invalid-report-type-msg [report-type]
  (format "The reportType `%s` is invalid. Please use one of `subject`, `subject-list` or `population`." report-type))


(def ^:private no-subject-list-on-get-msg
  "The reportType `subject-list` is not supported for GET requests. Please use POST.")


(defn- tx-ops [{:keys [tx-ops resource]} id]
  (conj tx-ops [:create (assoc resource :id id)]))


(defn- handle
  [clock node db executor
   {::reitit/keys [router] :keys [request-method headers]}
   params measure]
  (-> (ac/supply-async
        #(evaluate-measure (now clock) db router measure params)
        executor)
      (ac/then-compose
        (fn process-result [result]
          (if (::anom/category result)
            (ac/completed-future (handler-util/error-response result))
            (cond
              (= :get request-method)
              (ac/completed-future (ring/response (:resource result)))

              (= :post request-method)
              (let [id (luid)
                    return-preference (handler-util/preference headers "return")]
                (-> (d/transact node (tx-ops result id))
                    ;; it's important to switch to the transaction
                    ;; executor here, because otherwise the central
                    ;; indexing thread would execute response building.
                    (ac/then-apply-async identity executor)
                    (ac/then-compose
                      #(response/build-created-response
                         router return-preference % "MeasureReport" id))
                    (ac/exceptionally handler-util/error-response)))))))))


(defn- find-measure-handle
  [db {{:keys [id]} :path-params {:strs [measure]} :params}]
  (cond
    id
    (d/resource-handle db "Measure" id)

    measure
    (coll/first (d/type-query db "Measure" [["url" measure]]))))


(defn- conform-params
  [{:keys [request-method]
    {:strs [subject periodStart periodEnd reportType]} :params}]
  (let [report-type (or reportType (if subject "subject" "population"))]
    (cond
      (not (s/valid? :blaze.fhir.operation.evaluate-measure/report-type report-type))
      {::anom/category ::anom/incorrect
       ::anom/message (invalid-report-type-msg report-type)
       :fhir/issue "value"}

      (and (= :get request-method) (= "subject-list" report-type))
      {::anom/category ::anom/unsupported
       ::anom/message no-subject-list-on-get-msg}

      :else
      (let [period [periodStart periodEnd]]
        {:period period :report-type report-type}))))


(defn handler [clock node executor]
  (fn [request]
    (let [result (conform-params request)]
      (if (::anom/category result)
        (ac/completed-future (handler-util/error-response result))
        (let [db (d/db node)]
          (if-let [{:keys [op] :as measure-handle} (find-measure-handle db request)]
            (if (identical? :delete op)
              (-> (handler-util/operation-outcome
                    {:fhir/issue "deleted"})
                  (ring/response)
                  (ring/status 410)
                  (ac/completed-future))
              (-> (d/pull db measure-handle)
                  (ac/then-compose
                    #(handle
                       clock
                       node
                       db
                       executor
                       request
                       result
                       %))))
            (ac/completed-future
              (handler-util/error-response
                {::anom/category ::anom/not-found
                 :fhir/issue "not-found"}))))))))

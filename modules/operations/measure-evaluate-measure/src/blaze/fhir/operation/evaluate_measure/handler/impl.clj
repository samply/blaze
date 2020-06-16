(ns blaze.fhir.operation.evaluate-measure.handler.impl
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.fhir.operation.evaluate-measure.measure :refer [evaluate-measure]]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [manifold.deferred :as md]
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
   {::reitit/keys [router] :keys [request-method headers]
    {:strs [subject periodStart periodEnd reportType]} :params}
   measure]
  (let [report-type (or reportType (if subject "subject" "population"))]
    (cond
      (not (s/valid? :blaze.fhir.operation.evaluate-measure/report-type report-type))
      (handler-util/error-response
        {::anom/category ::anom/incorrect
         ::anom/message (invalid-report-type-msg report-type)
         :fhir/issue "value"})

      (and (= :get request-method) (= "subject-list" report-type))
      (handler-util/error-response
        {::anom/category ::anom/unsupported
         ::anom/message no-subject-list-on-get-msg})

      :else
      (let [period [periodStart periodEnd]
            params {:period period :report-type report-type}]
        (-> (md/future-with executor
              (evaluate-measure (now clock) db router measure params))
            (md/chain'
              (fn process-result [result]
                (if (::anom/category result)
                  (handler-util/error-response result)
                  (cond
                    (= :get request-method)
                    (ring/response (:resource result))

                    (= :post request-method)
                    (let [id (str (random-uuid))
                          return-preference (handler-util/preference headers "return")]
                      (-> (d/submit-tx node (tx-ops result id))
                          (md/chain'
                            #(response/build-created-response
                               router return-preference % "MeasureReport" id))
                          (md/catch' handler-util/error-response))))))))))))


(defn- find-measure
  [db {{:keys [id]} :path-params {:strs [measure]} :params}]
  (cond
    id
    (d/resource db "Measure" id)

    measure
    (coll/first (d/type-query db "Measure" [["url" measure]]))))


(defn handler [clock node executor]
  (fn [request]
    (let [db (d/db node)]
      (if-let [measure (find-measure db request)]
        (if (d/deleted? measure)
          (-> (handler-util/operation-outcome
                {:fhir/issue "deleted"})
              (ring/response)
              (ring/status 410))
          (handle
            clock
            node
            db
            executor
            request
            measure))
        (handler-util/error-response
          {::anom/category ::anom/not-found
           :fhir/issue "not-found"})))))

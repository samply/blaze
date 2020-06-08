(ns blaze.fhir.operation.evaluate-measure.handler.impl
  (:require
    [blaze.coll.core :as coll]
    [blaze.db.api :as d]
    [blaze.fhir.operation.evaluate-measure.measure :refer [evaluate-measure]]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.uuid :refer [random-uuid]]
    [cognitect.anomalies :as anom]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.util.response :as ring])
  (:import
    [java.time Clock OffsetDateTime]))


(set! *warn-on-reflection* true)


(defn- now [clock]
  (OffsetDateTime/now ^Clock clock))


(defn- handle
  [clock node db executor
   {::reitit/keys [router] :keys [request-method headers]
    {:strs [periodStart periodEnd]} :params}
   measure]
  (let [period [periodStart periodEnd]]
    (-> (md/future-with executor
          (evaluate-measure (now clock) node db router period measure))
        (md/chain'
          (fn process-result [result]
            (if (::anom/category result)
              (handler-util/error-response result)
              (cond
                (= :get request-method)
                (ring/response result)

                (= :post request-method)
                (let [id (str (random-uuid))
                      return-preference (handler-util/preference headers "return")]
                  (-> (d/submit-tx node [[:create (assoc result :id id)]])
                      (md/chain'
                        #(response/build-created-response
                           router return-preference % "MeasureReport" id))
                      (md/catch' handler-util/error-response))))))))))


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

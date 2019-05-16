(ns blaze.handler.util
  "HTTP/REST Handler Utils"
  (:require
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [org.apache.http HeaderElement]
    [org.apache.http.message BasicHeaderValueParser]))


(set! *warn-on-reflection* true)


(extend-protocol Datafiable
  HeaderElement
  (datafy [element]
    {:name (str/lower-case (.getName element))
     :value (.getValue element)}))


(defn parse-header-value
  "Parses the header value string `s` into elements which have a :name and a
  :value.

  The element name is converted to lower-case."
  [s]
  (when s
    (->> (BasicHeaderValueParser/parseElements
           s BasicHeaderValueParser/INSTANCE)
         (into [] (map datafy)))))


(defn preference
  "Returns the value of the preference with `name` from Ring `headers`."
  [headers name]
  (->> (parse-header-value (get headers "prefer"))
       (some #(when (= name (:name %)) (:value %)))))


(defn- operation-outcome
  [{:fhir/keys [issue operation-outcome] :or {issue "exception"}}]
  {:resourceType "OperationOutcome"
   :issue
   [(cond->
      {:severity "error"
       :code issue}
      operation-outcome
      (assoc
        :details
        {:coding
         [{:system "http://terminology.hl7.org/CodeSystem/operation-outcome"
           :code operation-outcome}]}))]})


(defn error-response [{::anom/keys [category] :as anomaly}]
  (cond
    category
    (do
      (log/error anomaly)
      (-> (ring/response (operation-outcome anomaly))
          (ring/status
            (case category
              ::anom/incorrect 400
              ::anom/not-found 404
              ::anom/conflict 409
              500))))

    (instance? Exception anomaly)
    (error-response
      (merge {::anom/message (.getMessage ^Exception anomaly)}
             (ex-data anomaly)))

    :else
    (error-response {::anom/category ::anom/fault})))

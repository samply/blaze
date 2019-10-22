(ns blaze.fhir.operation.evaluate-measure.middleware.params
  (:require
    [blaze.handler.util :as util]
    [cognitect.anomalies :as anom])
  (:import
    [java.time LocalDate Year YearMonth]))


(defn- parse-date [date]
  (case (count date)
    4 (Year/parse date)
    7 (YearMonth/parse date)
    (LocalDate/parse date)))


(defn- coerce-date [request param-name]
  (try
    (update-in request [:query-params param-name] parse-date)
    (catch Exception _
      {::anom/category ::anom/incorrect
       ::anom/message
       (str "Invalid parameter " param-name " `"
            (get-in request [:query-params param-name])
            "`. Should be a date in format YYYY, YYYY-MM or YYYY-MM-DD.")
       :fhir/issue "value"
       :fhir/operation-outcome "MSG_PARAM_INVALID"
       :fhir.issue/expression param-name})))


(defn- params-request [request]
  (let [request (coerce-date request "periodStart")]
    (if (::anom/category request)
      request
      (coerce-date request "periodEnd"))))


(defn wrap-coerce-params [handler]
  (fn [request]
    (let [request (params-request request)]
      (if (::anom/category request)
        (util/error-response request)
        (handler request)))))

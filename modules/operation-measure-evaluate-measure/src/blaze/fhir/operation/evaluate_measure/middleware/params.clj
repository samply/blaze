(ns blaze.fhir.operation.evaluate-measure.middleware.params
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.anomaly-spec]
    [blaze.async.comp :as ac]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.alpha :as s]))


(defn- invalid-date-param-msg [name value]
  (format "Invalid parameter `%s` with value `%s`. Should be a date in format YYYY, YYYY-MM or YYYY-MM-DD."
          name value))


(defn- coerce-date-param
  "Coerces the parameter with `name` from `request` into a System.Date.

  Returns an anomaly if the parameter is missing or invalid."
  [request name]
  (if-let [value (get-in request [:params name])]
    (-> (system/parse-date value)
        (ba/exceptionally
          (fn [_]
            (ba/incorrect
              (invalid-date-param-msg name value)
              :fhir/issue "value"
              :fhir/operation-outcome "MSG_PARAM_INVALID"
              :fhir.issue/expression name))))
    (ba/incorrect
      (format "Missing required parameter `%s`." name)
      :fhir/issue "value"
      :fhir/operation-outcome "MSG_PARAM_INVALID"
      :fhir.issue/expression name)))


(defn- invalid-report-type-param-msg [report-type]
  (format "Invalid parameter `reportType` with value `%s`. Should be one of `subject`, `subject-list` or `population`."
          report-type))


(def ^:private no-subject-list-on-get-msg
  "The parameter `reportType` with value `subject-list` is not supported for GET requests. Please use POST or one of `subject` or `population`.")


(defn- coerce-report-type-param
  "Coerces the parameter `reportType` from `request`.

  Returns an anomaly if the parameter is invalid."
  [{:keys [request-method] {:strs [reportType subject]} :params}]
  (let [report-type (or reportType (if subject "subject" "population"))]
    (cond
      (not (s/valid? :blaze.fhir.operation.evaluate-measure/report-type report-type))
      (ba/incorrect
        (invalid-report-type-param-msg report-type)
        :fhir/issue "value")

      (and (= :get request-method) (= "subject-list" report-type))
      (ba/unsupported no-subject-list-on-get-msg)

      :else
      report-type)))


(defn- invalid-subject-param-msg [subject]
  (format "Invalid parameter `subject` with value `%s`. Should be a reference."
          subject))


(defn- coerce-subject-ref-param [{{:strs [subject]} :params}]
  (when subject
    (let [local-ref (s/conform :blaze.fhir/local-ref subject)]
      (if (s/invalid? local-ref)
        (if (s/valid? :blaze.resource/id subject)
          subject
          (ba/incorrect
            (invalid-subject-param-msg subject)
            :fhir/issue "value"
            :fhir/operation-outcome "MSG_PARAM_INVALID"
            :fhir.issue/expression "subject"))
        local-ref))))


(defn- params-request [request]
  (when-ok [period-start (coerce-date-param request "periodStart")
            period-end (coerce-date-param request "periodEnd")
            report-type (coerce-report-type-param request)
            subject-ref (coerce-subject-ref-param request)]
    (assoc request
      :blaze.fhir.operation.evaluate-measure/params
      (cond-> {:period [period-start period-end]
               :report-type report-type}
        subject-ref
        (assoc :subject-ref subject-ref)))))


(defn wrap-coerce-params [handler]
  (fn [request]
    (if-ok [request (params-request request)]
      (handler request)
      ac/completed-future)))

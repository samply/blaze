(ns blaze.fhir.operation.evaluate-measure.middleware.params
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.fhir.operation.evaluate-measure.measure :as-alias measure]
   [blaze.fhir.operation.evaluate-measure.measure.spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [clojure.spec.alpha :as s]))

(defn- invalid-date-param-msg [name value]
  (format "Invalid parameter `%s` with value `%s`. Should be a date in format YYYY, YYYY-MM or YYYY-MM-DD."
          name value))

(defn- get-param-value-from-resource [body name]
  (when (identical? :fhir/Parameters (fhir-spec/fhir-type body))
    (some #(when (= name (:name %)) (:value %)) (:parameter body))))

(defn- get-param-value [{:keys [params body]} name coercer]
  (or (some->> (get params name) (coercer name))
      (get-param-value-from-resource body name)))

(defn- get-required-param-value [request name coercer]
  (or (get-param-value request name coercer)
      (ba/incorrect
       (format "Missing required parameter `%s`." name)
       :fhir/issue "value"
       :fhir/operation-outcome "MSG_PARAM_INVALID"
       :fhir.issue/expression name)))

(defn- invalid-date-param-anom
  [name value]
  (ba/incorrect
   (invalid-date-param-msg name value)
   :fhir/issue "value"
   :fhir/operation-outcome "MSG_PARAM_INVALID"
   :fhir.issue/expression name))

(defn- coerce-date
  "Coerces `value` into a System.Date.

  Returns an anomaly if the parameter is invalid."
  [name value]
  (-> (system/parse-date value)
      (ba/exceptionally
       (fn [_]
         (invalid-date-param-anom name value)))))

(defn- invalid-report-type-param-msg [report-type]
  (format "Invalid parameter `reportType` with value `%s`. Should be one of `subject`, `subject-list` or `population`."
          report-type))

(defn- coerce-report-type [_ value]
  (if-not (s/valid? ::measure/report-type value)
    (ba/incorrect (invalid-report-type-param-msg value) :fhir/issue "value")
    (type/code value)))

(defn- invalid-subject-param-msg [subject]
  (format "Invalid parameter `subject` with value `%s`. Should be a reference."
          subject))

(defn- coerce-subject-ref-param [{{:strs [subject]} :params}]
  (when subject
    (let [literal-ref (s/conform :blaze.fhir/literal-ref subject)]
      (if (s/invalid? literal-ref)
        (if (s/valid? :blaze.resource/id subject)
          subject
          (ba/incorrect
           (invalid-subject-param-msg subject)
           :fhir/issue "value"
           :fhir/operation-outcome "MSG_PARAM_INVALID"
           :fhir.issue/expression "subject"))
        literal-ref))))

(def ^:private no-subject-list-on-get-msg
  "The parameter `reportType` with value `subject-list` is not supported for GET requests. Please use POST or one of `subject` or `population`.")

(defn- params-request [{:keys [request-method] :as request}]
  (when-ok [period-start (get-required-param-value
                          request "periodStart" coerce-date)
            period-end (get-required-param-value
                        request "periodEnd" coerce-date)
            measure (get-param-value
                     request "measure" (fn [_n v] v))
            report-type (get-param-value
                         request "reportType" coerce-report-type)
            subject-ref (coerce-subject-ref-param request)]
    (let [report-type (some-> report-type type/value)]
      (if (and (= :get request-method) (= "subject-list" report-type))
        (ba/unsupported no-subject-list-on-get-msg)
        (assoc request
               :blaze.fhir.operation.evaluate-measure/params
               (cond-> {:period [period-start period-end]
                        :report-type (or report-type
                                         (if subject-ref "subject" "population"))}
                 measure
                 (assoc :measure measure)
                 subject-ref
                 (assoc :subject-ref subject-ref)))))))

(defn wrap-coerce-params [handler]
  (fn [request]
    (if-ok [request (params-request request)]
      (handler request)
      ac/completed-future)))

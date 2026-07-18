(ns blaze.fhir.operation.compact
  "Main entry point into the $compact operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler :as js]
   [blaze.job.compact :as job-compact]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- parameters [{:fhir/keys [type] :as body}]
  (if (= :fhir/Parameters type)
    body
    (ba/incorrect (format "Expected Parameters resource but was `%s` resource." (name type)))))

(defn- coerce [spec msg]
  (fn [value]
    (let [value (:value value)]
      (if (s/valid? spec value)
        value
        (ba/incorrect (format msg value))))))

(def ^:private param-specs
  "Specs of the job parameters."
  {"database"
   {:action :copy
    :required true
    :coerce (coerce ::job-compact/database "Should be one of `index`, `transaction` or `resource` but was `%s`.")}
   "column-family"
   {:action :copy
    :required true
    :coerce (coerce ::job-compact/column-family "Has to be a column-family name.")}})

(defmethod m/pre-init-spec :blaze.fhir.operation/compact [_]
  (s/keys :req-un [:blaze/clock] :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.fhir.operation/compact
  [_ {:keys [clock] :as context}]
  (log/info "Init FHIR $compact operation handler")
  (fn [{:keys [body] :blaze/keys [job-scheduler] :as request}]
    (if-ok [parameters (parameters body)
            params (fu/coerce-params param-specs parameters)]
      (let [authored-on (time/offset-date-time clock)]
        (log/debug "Initiate async response...")
        (do-sync [job (js/create-job job-scheduler (job-compact/job authored-on params))]
          (-> (ring/status 202)
              (handler-util/async-status-location context request job))))
      ac/completed-future)))

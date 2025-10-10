(ns blaze.fhir.operation.compact
  "Main entry point into the $compact operation."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.job-scheduler :as js]
   [blaze.job.compact :as job-compact]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- parameters [{:fhir/keys [type] :as body}]
  (if (= :fhir/Parameters type)
    body
    (ba/incorrect (format "Expected Parameters resource but was `%s` resource." (name type)))))

(defn- get-param [{:keys [parameter]} name]
  (or (some #(when (= name (-> % :name :value)) (-> % :value :value)) parameter)
      (ba/incorrect (format "Missing `%s` parameter." name))))

(defn- async-status-url
  [{:keys [context-path] :or {context-path ""}} {:blaze/keys [base-url]} {:keys [id]}]
  (str base-url context-path "/__async-status/" id))

(defmethod m/pre-init-spec :blaze.fhir.operation/compact [_]
  (s/keys :req-un [:blaze/clock] :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.fhir.operation/compact
  [_ {:keys [clock] :as context}]
  (log/info "Init FHIR $compact operation handler")
  (fn [{:keys [body] :blaze/keys [job-scheduler] :as request}]
    (if-ok [parameters (parameters body)
            database (get-param parameters "database")
            column-family (get-param parameters "column-family")]
      (let [authored-on (time/offset-date-time clock)]
        (log/debug "Initiate async response...")
        (do-sync [job (js/create-job job-scheduler (job-compact/job authored-on database column-family))]
          (-> (ring/status 202)
              (ring/header "Content-Location" (async-status-url context request job)))))
      ac/completed-future)))

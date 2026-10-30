(ns blaze.fhir.operation.compact
  "Main entry point into the $compact operation."
  (:require
   [blaze.anomaly :as ba]
   [blaze.handler.async-job :as async-job]
   [blaze.job.compact :as job-compact]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

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
  [_ context]
  (log/info "Init FHIR $compact operation handler")
  (async-job/handler context param-specs job-compact/job))

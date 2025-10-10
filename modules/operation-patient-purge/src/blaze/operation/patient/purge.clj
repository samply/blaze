(ns blaze.operation.patient.purge
  "Main entry point into the Patient $purge operation."
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- diagnostics [id]
  (format "The patient with id `%s` was purged successfully." id))

(defn- handler [{:keys [node]}]
  (fn [{{:keys [id]} :path-params}]
    (do-sync [_ (d/transact node [[:patient-purge id]])]
      (ring/response
       {:fhir/type :fhir/OperationOutcome
        :issue [{:fhir/type :fhir.OperationOutcome/issue
                 :severity #fhir/code "success"
                 :code #fhir/code "success"
                 :diagnostics (type/string (diagnostics id))}]}))))

(defmethod m/pre-init-spec :blaze.operation.patient/purge [_]
  (s/keys :req-un [:blaze.db/node]))

(defmethod ig/init-key :blaze.operation.patient/purge [_ context]
  (log/info "Init FHIR Patient $purge operation handler")
  (handler context))

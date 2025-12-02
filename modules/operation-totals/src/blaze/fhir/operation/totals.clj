(ns blaze.fhir.operation.totals
  "Main entry point into the $totals operation."
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.structure-definition-repo :as sdr]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- parameter [db type]
  (let [total (d/type-total db type)]
    (when (pos? total)
      {:fhir/type :fhir.Parameters/parameter
       :name (type/string type)
       :value (type/unsignedInt total)})))

(defn- parameters [db types]
  {:fhir/type :fhir/Parameters
   :parameter (into [] (keep (partial parameter db)) types)})

(defn- handler [types]
  (fn [{:blaze/keys [db]}]
    (-> (ring/response (parameters db types))
        (ac/completed-future))))

(defmethod m/pre-init-spec :blaze.fhir.operation/totals [_]
  (s/keys :req-un [:blaze.fhir/structure-definition-repo]))

(defmethod ig/init-key :blaze.fhir.operation/totals
  [_ {:keys [structure-definition-repo]}]
  (log/info "Init FHIR $totals operation handler")
  (handler (mapv :name (sdr/resources structure-definition-repo))))

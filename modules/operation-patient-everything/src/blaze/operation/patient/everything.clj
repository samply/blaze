(ns blaze.operation.patient.everything
  "Main entry point into the $everything operation."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.interaction.search.util :as search-util]
   [blaze.luid :as luid]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private ^:const ^long max-size 10000)

(defn- patient-handle [db id]
  (when-let [{:keys [op] :as handle} (d/resource-handle db "Patient" id)]
    (when-not (identical? :delete op)
      handle)))

(defn- too-costly-msg [patient-id]
  (format "The compartment of the Patient with the id `%s` has more than %d resources which is too costly to output. Please use type search with rev-include or compartment search instead." patient-id max-size))

(defn- handles [db patient-id]
  (if-let [patient (patient-handle db patient-id)]
    (let [handles (into [] (take max-size) (d/rev-include db patient))]
      (if (= max-size (count handles))
        (ba/conflict (too-costly-msg patient-id) :fhir/issue "too-costly")
        (into [patient] handles)))
    (ba/not-found (format "The Patient with id `%s` was not found." patient-id))))

(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))

(defn- bundle [context resources]
  (let [entries (mapv (partial search-util/entry context) resources)]
    {:fhir/type :fhir/Bundle
     :id (luid context)
     :type #fhir/code"searchset"
     :total (type/->UnsignedInt (count entries))
     :entry entries}))

(defmethod ig/pre-init-spec :blaze.operation.patient/everything [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]))

(defmethod ig/init-key :blaze.operation.patient/everything [_ context]
  (log/info "Init FHIR Patient $everything operation handler")
  (fn [{{:keys [id]} :path-params :blaze/keys [db] :as request}]
    (when-ok [handles (handles db id)]
      (do-sync [resources (d/pull-many db handles)]
        (ring/response (bundle (merge context request) resources))))))

(ns blaze.operation.patient.everything
  "Main entry point into the Patient $everything operation."
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.luid :as luid]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private ^:const ^long max-size 10000)

(defn- patient-handle [db id]
  (when-let [{:keys [op] :as handle} (d/resource-handle db "Patient" id)]
    (when-not (identical? :delete op)
      handle)))

(defn- too-costly-msg [patient-id]
  (format "The compartment of the Patient with the id `%s` has more than %d resources which is too costly to output. Please use paging by specifying the _count query param." patient-id max-size))

(defn- handles-xf [page-offset page-size]
  (comp (drop page-offset) (take (inc (or page-size max-size)))))

(defn- handles [db patient-id query-params page-size]
  (if-let [patient (patient-handle db patient-id)]
    (when-ok [start (fhir-util/date query-params "start")
              end (fhir-util/date query-params "end")]
      (let [page-offset (fhir-util/page-offset query-params)
            handles (into [] (handles-xf page-offset page-size)
                          (d/patient-everything db patient start end))]
        (if page-size
          (if (< page-size (count handles))
            {:handles (pop handles)
             :next-offset (+ page-offset (dec (count handles)))}
            {:handles handles})
          (if (< max-size (count handles))
            (ba/conflict (too-costly-msg patient-id) :fhir/issue "too-costly")
            {:handles handles}))))
    (ba/not-found (format "The Patient with id `%s` was not found." patient-id))))

(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))

(defn- next-link
  [{:blaze/keys [base-url db] ::reitit/keys [match]} page-size offset]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (str base-url (reitit/match->path match {"_count" page-size
                                                 "__t" (d/t db)
                                                 "__page-offset" offset}))})

(defn- bundle [context request resources page-size next-offset]
  (let [entries (mapv (partial search-util/entry request) resources)]
    (cond->
     {:fhir/type :fhir/Bundle
      :id (luid context)
      :type #fhir/code"searchset"
      :entry entries}

      (some? next-offset)
      (assoc :link [(next-link request page-size next-offset)])

      (nil? page-size)
      (assoc :total (type/->UnsignedInt (count entries))))))

(defn- handler [context]
  (fn [{:blaze/keys [db]
        {:keys [id]} :path-params
        :keys [query-params] :as request}]
    (let [page-size (fhir-util/page-size query-params max-size nil)]
      (when-ok [{:keys [handles next-offset]} (handles db id query-params page-size)]
        (do-sync [resources (d/pull-many db handles)]
          (ring/response (bundle context request resources page-size next-offset)))))))

(defmethod ig/pre-init-spec :blaze.operation.patient/everything [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]))

(defmethod ig/init-key :blaze.operation.patient/everything [_ context]
  (log/info "Init FHIR Patient $everything operation handler")
  (handler context))

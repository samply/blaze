(ns blaze.operation.patient.everything
  "Main entry point into the Patient $everything operation."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(def ^:private ^:const ^long max-size 10000)

(defn- too-costly-msg [patient-id]
  (format "The compartment of the Patient with the id `%s` has more than %,d resources which is too costly to output. Please use paging by specifying the _count query param." patient-id max-size))

(defn- handles-xf [page-offset page-size]
  (comp (drop page-offset) (take (inc (or page-size max-size)))))

(defn- handles [db patient-id start end page-offset page-size]
  (when-ok [patient (fhir-util/resource-handle db "Patient" patient-id)]
    (let [handles (into [] (handles-xf page-offset page-size)
                        (d/patient-everything db patient start end))]
      (if page-size
        (if (< page-size (count handles))
          {:handles (pop handles)
           :next-offset (+ page-offset (dec (count handles)))}
          {:handles handles})
        (if (< max-size (count handles))
          (ba/conflict (too-costly-msg patient-id) :fhir/issue "too-costly")
          {:handles handles})))))

(defn- page-match [{::reitit/keys [router] {:keys [id]} :path-params} page-id]
  (reitit/match-by-name router :Patient.operation/everything-page
                        {:id id :page-id page-id}))

(defn- next-link
  [{:keys [page-id-cipher]} {:blaze/keys [base-url db] :as request}
   start end page-size offset]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (->> (cond->
              {"_count" (str page-size)
               "__t" (str (d/t db))
               "__page-offset" (str offset)}
               start
               (assoc "start" (str start))
               end
               (assoc "end" (str end)))
             (decrypt-page-id/encrypt page-id-cipher)
             (page-match request)
             (reitit/match->path)
             (str base-url))})

(defn- bundle [context request resources start end page-size next-offset]
  (let [entries (mapv (partial search-util/match-entry request) resources)]
    (cond->
     {:fhir/type :fhir/Bundle
      :id (m/luid context)
      :type #fhir/code"searchset"
      :entry entries}

      (some? next-offset)
      (assoc :link [(next-link context request start end page-size next-offset)])

      (nil? page-size)
      (assoc :total (type/->UnsignedInt (count entries))))))

(defn- handler [context]
  (fn [{:blaze/keys [db]
        {:keys [id]} :path-params
        :keys [query-params] :as request}]
    (let [page-size (fhir-util/page-size query-params max-size nil)
          page-offset (fhir-util/page-offset query-params)]
      (when-ok [start (fhir-util/date query-params "start")
                end (fhir-util/date query-params "end")
                {:keys [handles next-offset]} (handles db id start end
                                                       page-offset page-size)]
        (do-sync [resources (d/pull-many db handles)]
          (ring/response (bundle context request resources start end page-size
                                 next-offset)))))))

(defmethod m/pre-init-spec :blaze.operation.patient/everything [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.operation.patient/everything [_ context]
  (log/info "Init FHIR Patient $everything operation handler")
  (handler context))

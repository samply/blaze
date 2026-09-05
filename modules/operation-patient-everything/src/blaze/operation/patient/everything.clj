(ns blaze.operation.patient.everything
  "Main entry point into the Patient $everything operation."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
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

(defn- handles [db patient-id start end page-offset page-size since]
  (when-ok [patient (fhir-util/resource-handle db "Patient" patient-id)]
    (let [since-db (cond-> db since (d/since since))
          handles (into [] (handles-xf page-offset page-size)
                        (d/patient-everything since-db patient start end))]
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
  [{::search-util/keys [link] :keys [page-id-cipher]}
   {:blaze/keys [base-url db] :as request} start end page-size offset since]
  (->> (cond->
        {"_count" (str page-size)
         "__t" (str (d/t db))
         "__page-offset" (str offset)}
         start
         (assoc "start" (str start))
         end
         (assoc "end" (str end))
         since
         (assoc "_since" (str since)))
       (decrypt-page-id/encrypt page-id-cipher)
       (page-match request)
       (reitit/match->path)
       (str base-url)
       (link "next")))

(defn- bundle [context request resources start end page-size next-offset since]
  (let [entries (mapv (partial search-util/match-entry request) resources)]
    (cond->
     {:fhir/type :fhir/Bundle
      :id (m/luid context)
      :type #fhir/code "searchset"
      :entry entries}

      (some? next-offset)
      (assoc :link [(next-link context request start end page-size next-offset since)])

      (nil? page-size)
      (assoc :total (type/unsignedInt (count entries))))))

(defn- coerce-count [value]
  (when-ok [value (fu/coerce-integer value)]
    (if (nat-int? value)
      (min value max-size)
      (ba/incorrect "Has to be a non-negative integer."))))

(def ^:private param-specs
  "Specs of the params, coerced from a Parameters resource.

  The param `_type` of the R4 operation isn't supported. It has no :action, so
  that it is reported as unsupported instead of being silently ignored."
  {"start" {:action :copy :coerce fu/coerce-date}
   "end" {:action :copy :coerce fu/coerce-date}
   "_since" {:action :copy :key :since :coerce fu/coerce-instant}
   "_count" {:action :copy :key :page-size :coerce coerce-count}
   "_type" {}})

(def ^:private unsupported-params
  "Names of the params that are known but not supported, taken from
  `param-specs`."
  (into #{} (keep (fn [[name {:keys [action]}]] (when-not action name))) param-specs))

(defn- check-unsupported-params [query-params]
  (some
   (fn [name]
     (when (contains? query-params name)
       (ba/unsupported (format "Unsupported parameter `%s`." name)
                       :http/status 400)))
   unsupported-params))

(defn- params-from-query
  "Returns the params taken from `query-params`.

  Only params that are actually given are part of the result."
  [query-params]
  (when-ok [_ (check-unsupported-params query-params)
            start (fhir-util/date query-params "start")
            end (fhir-util/date query-params "end")]
    (let [since (fhir-util/since query-params)
          page-size (fhir-util/page-size query-params max-size nil)]
      (cond-> {}
        start (assoc :start start)
        end (assoc :end end)
        since (assoc :since since)
        page-size (assoc :page-size page-size)))))

(defn- params-from-body
  "Returns the params taken from the Parameters resource `body` or nil if `body`
  isn't a Parameters resource."
  [body]
  (when (identical? :fhir/Parameters (:fhir/type body))
    (fu/coerce-params param-specs body)))

(defn- params
  "Returns the params of `request`.

  The params are taken from a Parameters resource in the body of a POST request,
  overridden by the query params."
  [{:keys [body query-params]}]
  (when-ok [params (params-from-query query-params)
            body-params (params-from-body body)]
    (cond->> params body-params (merge body-params))))

(defn- handler [context]
  (fn [{:blaze/keys [db]
        {:keys [id]} :path-params
        :keys [query-params] :as request}]
    (let [page-offset (fhir-util/page-offset query-params)]
      (when-ok [{:keys [start end since page-size]} (params request)
                {:keys [handles next-offset]}
                (handles db id start end page-offset page-size since)]
        (do-sync [resources (d/pull-many db handles)]
          (ring/response (bundle context request resources start end page-size
                                 next-offset since)))))))

(defmethod m/pre-init-spec :blaze.operation.patient/everything [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.operation.patient/everything [_ context]
  (log/info "Init FHIR Patient $everything operation handler")
  (handler context))

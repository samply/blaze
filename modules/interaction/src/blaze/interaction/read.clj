(ns blaze.interaction.read
  "FHIR read interaction.

  https://www.hl7.org/fhir/http.html#read"
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-async]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- resource-handle [db type id]
  (if-let [{:keys [op t] :as handle} (d/resource-handle db type id)]
    (if (identical? :delete op)
      (let [tx (d/tx db t)]
        (ba/not-found
         (format "Resource `%s/%s` was deleted." type id)
         :http/status 410
         :http/headers
         [["Last-Modified" (fhir-util/last-modified tx)]
          ["ETag" (fhir-util/etag tx)]]
         :fhir/issue "deleted"))
      handle)
    (ba/not-found
     (format "Resource `%s/%s` was not found." type id)
     :fhir/issue "not-found")))

(defn- pull [db type id]
  (if-ok [resource-handle (resource-handle db type id)]
    (-> (d/pull db resource-handle)
        (ac/exceptionally
         #(assoc %
                 ::anom/category ::anom/fault
                 :fhir/issue "incomplete")))
    ac/completed-future))

(defn- response [resource]
  (let [{:blaze.db/keys [tx]} (meta resource)]
    (-> (ring/response resource)
        (ring/header "Last-Modified" (fhir-util/last-modified tx))
        (ring/header "ETag" (fhir-util/etag tx)))))

(def ^:private handler
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params :blaze/keys [db]}]
    (do-async [resource (pull db type id)]
      (response resource))))

(defn- wrap-invalid-id [handler]
  (fn [{{:keys [id]} :path-params :as request}]
    (cond
      (not (re-matches #"[A-Za-z0-9\-\.]{1,64}" id))
      (ac/completed-future
       (ba/incorrect
        (format "Resource id `%s` is invalid." id)
        :fhir/issue "value"
        :fhir/operation-outcome "MSG_ID_INVALID"))

      :else
      (handler request))))

(defmethod ig/init-key :blaze.interaction/read [_ _]
  (log/info "Init FHIR read interaction handler")
  (wrap-invalid-id handler))

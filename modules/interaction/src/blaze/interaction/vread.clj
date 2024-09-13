(ns blaze.interaction.vread
  "FHIR read interaction.

  https://www.hl7.org/fhir/http.html#read"
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp  :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.util :as iu]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(defn- not-found-anom
  ([type id]
   (ba/not-found
    (format "Resource `%s/%s` with the given version was not found." type id)
    :http/headers [["Cache-Control" "no-cache"]]))
  ([type id t]
   (ba/not-found
    (format "Resource `%s/%s` with version `%d` was not found." type id t)
    :http/headers [["Cache-Control" "no-cache"]])))

(def ^:private handler
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id vid]} :path-params :blaze/keys [db]}]
    (if-let [t (fhir-util/parse-nat-long vid)]
      (if (<= t (d/t db))
        (do-sync [resource (fhir-util/pull-historic db type id t)]
          (iu/response resource))
        (ac/completed-future (not-found-anom type id t)))
      (ac/completed-future (not-found-anom type id)))))

(defmethod ig/init-key :blaze.interaction/vread [_ _]
  (log/info "Init FHIR read interaction handler")
  (iu/wrap-invalid-id handler))

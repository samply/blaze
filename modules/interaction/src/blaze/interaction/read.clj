(ns blaze.interaction.read
  "FHIR read interaction.

  https://www.hl7.org/fhir/http.html#read"
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.util :as iu]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(def ^:private handler
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params :blaze/keys [db]}]
    (do-sync [resource (fhir-util/pull db type id)]
      (iu/response resource))))

(defmethod ig/init-key :blaze.interaction/read [_ _]
  (log/info "Init FHIR read interaction handler")
  (iu/wrap-invalid-id handler))

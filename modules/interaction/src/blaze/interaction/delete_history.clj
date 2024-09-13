(ns blaze.interaction.delete-history
  "FHIR delete-history interaction.

  https://build.fhir.org/http.html#delete-history"
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defmethod m/pre-init-spec :blaze.interaction/delete-history [_]
  (s/keys :req-un [:blaze.db/node]))

(defmethod ig/init-key :blaze.interaction/delete-history [_ {:keys [node]}]
  (log/info "Init FHIR delete-history interaction handler")
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (do-sync [_ (d/transact node [[:delete-history type id]])]
      (ring/status 204))))

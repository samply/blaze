(ns blaze.interaction.delete-history-version
  "FHIR delete-history-version interaction.

  https://build.fhir.org/http.html#delete-history-version"
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.interaction.util :as iu]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defmethod m/pre-init-spec :blaze.interaction/delete-history-version [_]
  (s/keys :req-un [:blaze.db/node]))

(defmethod ig/init-key :blaze.interaction/delete-history-version
  [_ {:keys [node]}]
  (log/info "Init FHIR delete-history-version interaction handler")
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id vid]} :path-params}]
    (if-let [t (iu/vid->t vid)]
      (do-sync [_ (d/transact node [[:delete-history-version type id t]])]
        (ring/status 204))
      (ac/completed-future (ring/status 204)))))

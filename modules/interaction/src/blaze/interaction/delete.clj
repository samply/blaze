(ns blaze.interaction.delete
  "FHIR delete interaction.

  https://www.hl7.org/fhir/http.html#delete"
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- build-response [db]
  (let [tx (d/tx db (d/basis-t db))]
    (-> (ring/response nil)
        (ring/status 204)
        (ring/header "Last-Modified" (fhir-util/last-modified tx))
        (ring/header "ETag" (fhir-util/etag tx)))))

(defmethod m/pre-init-spec :blaze.interaction/delete [_]
  (s/keys :req-un [:blaze.db/node]))

(defmethod ig/init-key :blaze.interaction/delete [_ {:keys [node]}]
  (log/info "Init FHIR delete interaction handler")
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (do-sync [db (d/transact node [[:delete type id]])]
      (build-response db))))

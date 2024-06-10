(ns blaze.rest-api.batch-handler
  (:require
   [blaze.handler.util :as handler-util]
   [blaze.module :as m]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.routes :as routes]
   [blaze.rest-api.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.ring]))

(defmethod m/pre-init-spec ::rest-api/batch-handler [_]
  (s/keys
   :req-un
   [:blaze.fhir/structure-definition-repo
    ::rest-api/capabilities-handler
    ::resource-patterns]
   :opt-un
   [:blaze/context-path
    ::search-system-handler
    ::transaction-handler
    ::history-system-handler
    ::operations
    ::metadata-handler
    ::admin-handler
    :blaze.db/enforce-referential-integrity]))

(def ^:private remove-middleware
  (remove (comp #{:resource :auth-guard :output :forwarded :sync
                  :db :snapshot-db :versioned-instance-db :error
                  :observe-request-duration} :name)))

(defmethod ig/init-key ::rest-api/batch-handler
  [_ {:keys [context-path] :or {context-path ""} :as config}]
  (reitit.ring/ring-handler
   (reitit.ring/router
    (routes/routes (assoc config :batch? true))
    {:path context-path
     :syntax :bracket
     :reitit.middleware/transform
     (fn [middleware]
       (into [] remove-middleware middleware))})
   handler-util/default-batch-handler))

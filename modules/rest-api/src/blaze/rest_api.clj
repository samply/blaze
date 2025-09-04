(ns blaze.rest-api
  (:require
   [blaze.db.spec]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.writing-context.spec]
   [blaze.handler.fhir.util.spec]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler.spec]
   [blaze.middleware.fhir.output :as fhir-output]
   [blaze.middleware.fhir.resource :as resource]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.rest-api.middleware.log :refer [wrap-log]]
   [blaze.rest-api.middleware.metrics :as metrics]
   [blaze.rest-api.routes :as routes]
   [blaze.rest-api.spec]
   [blaze.rest-api.structure-definitions :as structure-definitions]
   [blaze.spec]
   [blaze.validator.spec]
   [buddy.auth.middleware :refer [wrap-authentication]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.ring]
   [reitit.ring.spec]
   [taoensso.timbre :as log]))

(def ^:private wrap-job-scheduler
  {:name :job-scheduler
   :wrap (fn [handler job-scheduler]
           (fn [request respond raise]
             (handler (assoc request :blaze/job-scheduler job-scheduler) respond raise)))})

(defn- router [{:keys [context-path] :or {context-path ""} :as config}]
  (reitit.ring/router
   (routes/routes config)
   {:path context-path
    :syntax :bracket}))

(defn- handler
  "Whole app Ring handler."
  [{:keys [job-scheduler writing-context auth-backends] :as config}]
  (-> (reitit.ring/ring-handler
       (router config)
       (reitit.ring/routes
        (reitit.ring/redirect-trailing-slash-handler {:method :strip})
        (fhir-output/wrap-output handler-util/default-handler writing-context {:accept-all? true}))
       {:middleware
        (cond-> [[wrap-job-scheduler job-scheduler]]
          (seq auth-backends)
          (conj #(apply wrap-authentication % auth-backends)))})
      wrap-log))

(defmethod m/pre-init-spec :blaze/rest-api [_]
  (s/keys
   :req-un
   [:blaze/base-url
    :blaze.fhir/parsing-context
    :blaze.fhir/writing-context
    :blaze.fhir/structure-definition-repo
    :blaze.db/node
    ::admin-node
    :blaze/job-scheduler
    :blaze/clock
    :blaze/rng-fn
    ::async-status-handler
    ::async-status-cancel-handler
    ::capabilities-handler
    ::db-sync-timeout
    :blaze/page-id-cipher]
   :opt-un
   [:blaze/context-path
    :blaze/validator
    ::auth-backends
    ::search-system-handler
    ::transaction-handler
    ::history-system-handler
    ::resource-patterns
    ::operations
    ::admin-handler
    :blaze.db/enforce-referential-integrity]))

(defmethod ig/init-key :blaze/rest-api
  [_ {:keys [base-url context-path db-sync-timeout] :as config}]
  (log/info "Init FHIR RESTful API with base URL:" (str base-url context-path)
            "and a database sync timeout of" db-sync-timeout "ms")
  @(structure-definitions/ensure-structure-definitions config)
  (handler config))

(reg-collector ::requests-total
  metrics/requests-total)

(reg-collector ::request-duration-seconds
  metrics/request-duration-seconds)

(reg-collector ::parse-duration-seconds
  resource/parse-duration-seconds)

(reg-collector ::generate-duration-seconds
  fhir-output/generate-duration-seconds)

(defmethod ig/init-key :blaze.rest-api/resource-patterns
  [_ patterns]
  (into
   []
   (map
    (fn [[type interactions]]
      #:blaze.rest-api.resource-pattern{:type type :interactions interactions}))
   patterns))

(defmethod ig/init-key :blaze.rest-api/operations
  [_ operations]
  operations)

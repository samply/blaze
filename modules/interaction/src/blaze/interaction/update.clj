(ns blaze.interaction.update
  "FHIR update interaction.

  https://www.hl7.org/fhir/http.html#update"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.executors :refer [executor?]]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.terminology-service :refer [term-service?]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [ring.util.time :as ring-time]
    [taoensso.timbre :as log]))


(defn- validate-resource [type id body]
  (cond
    (not (map? body))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "structure"
       :fhir/operation-outcome "MSG_JSON_OBJECT"})

    (not= type (get body "resourceType"))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"})

    (not= id (get body "id"))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"})

    :else
    body))


(defn- build-response
  [router headers type id old-resource {db :db-after}]
  (let [basis-transaction (util/basis-transaction db)
        last-modified (:db/txInstant basis-transaction)
        return-preference (handler-util/preference headers "return")
        vid (str (d/basis-t db))]
    (cond->
      (-> (cond
            (= "minimal" return-preference)
            nil
            (= "OperationOutcome" return-preference)
            {:resourceType "OperationOutcome"}
            :else
            (pull/pull-resource db type id))
          (ring/response)
          (ring/status (if old-resource 200 201))
          (ring/header "Last-Modified" (ring-time/format-date last-modified))
          (ring/header "ETag" (str "W/\"" vid "\"")))
      (nil? old-resource)
      (ring/header
        "Location" (fhir-util/versioned-instance-url router type id vid)))))


(defn- handler-intern [transaction-executor conn term-service]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        :keys [headers body]
        ::reitit/keys [router]}]
    (let [db (d/db conn)]
      (-> (validate-resource type id body)
          (md/chain'
            #(fhir-util/upsert-resource
               transaction-executor
               conn
               term-service
               db
               :client-assigned-id
               %))
          (md/chain'
            #(build-response
               router headers type id (util/resource db type id) %))
          (md/catch' handler-util/error-response)))))


(s/def :handler.fhir/update fn?)


(s/fdef handler
  :args
  (s/cat
    :transaction-executor executor?
    :conn ::ds/conn
    :term-service term-service?)
  :ret :handler.fhir/update)

(defn handler
  ""
  [transaction-executor conn term-service]
  (-> (handler-intern transaction-executor conn term-service)
      (wrap-observe-request-duration "update")))


(defmethod ig/init-key :blaze.interaction/update
  [_ {:database/keys [transaction-executor conn] :keys [term-service]}]
  (log/info "Init FHIR update interaction handler")
  (handler transaction-executor conn term-service))

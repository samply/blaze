(ns blaze.handler.fhir.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [ring.util.time :as ring-time]))


(defn- validate-resource [type body]
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

    :else
    body))


(defn- build-response [router return-preference type id {db :db-after}]
  (let [last-modified (:db/txInstant (util/basis-transaction db))
        vid (str (d/basis-t db))]
    (-> (ring/created
          (handler-fhir-util/versioned-instance-url router type id vid)
          (cond
            (= "minimal" return-preference)
            nil
            (= "OperationOutcome" return-preference)
            {:resourceType "OperationOutcome"}
            :else
            (pull/pull-resource db type id)))
        (ring/header "Last-Modified" (ring-time/format-date last-modified))
        (ring/header "ETag" (str "W/\"" vid "\"")))))


(defn- handler-intern [conn]
  (fn [{{:keys [type]} :path-params :keys [headers body] ::reitit/keys [router]}]
    (let [return-preference (handler-util/preference headers "return")
          id (str (d/squuid))]
      (-> (validate-resource type body)
          (md/chain' #(assoc % "id" id))
          (md/chain'
            #(handler-fhir-util/upsert-resource
               conn (d/db conn) :server-assigned-id %))
          (md/chain' #(build-response router return-preference type id %))
          (md/catch' handler-util/error-response)))))


(s/def :handler.fhir/create fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/create)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-observe-request-duration "create")))

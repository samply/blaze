(ns blaze.terminology-service.local
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.module :as m]
   [blaze.spec]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.local.capabilities :as c]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system.sct :as sct]
   [blaze.terminology-service.local.code-system.ucum :as ucum]
   [blaze.terminology-service.local.spec]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.expand :as vs-expand]
   [blaze.terminology-service.local.value-set.validate-code :as vs-validate-code]
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn- non-deleted-resource-handle [db type id]
  (when-let [handle (d/resource-handle db type id)]
    (when-not (d/deleted? handle)
      handle)))

(defn- code-system-not-found-by-id-anom [id]
  (ba/not-found (format "The code system with id `%s` was not found." id)))

(defn- find-code-system-by-id [{:keys [db] :as context} id]
  (if-let [code-system-handle (non-deleted-resource-handle db "CodeSystem" id)]
    (do-sync [code-system (d/pull db code-system-handle)]
      (cs/enhance context code-system))
    (ac/completed-future (code-system-not-found-by-id-anom id))))

(defn- find-code-system [context {:keys [id url code-system version]}]
  (cond
    code-system (ac/completed-future code-system)
    (and url version) (cs/find context url version)
    url (cs/find context url)
    id (find-code-system-by-id context id)
    :else (ac/completed-future (ba/incorrect "Missing ID or URL."))))

(defn- value-set-not-found-by-id-anom [id]
  (ba/not-found (format "The value set with id `%s` was not found." id)))

(defn- find-value-set-by-id [db id]
  (if-let [value-set (non-deleted-resource-handle db "ValueSet" id)]
    (d/pull db value-set)
    (ac/completed-future (value-set-not-found-by-id-anom id))))

(defn- find-value-set
  [{:keys [db]
    {:keys [id url value-set] version :value-set-version} :request
    :as context}]
  (cond
    value-set (ac/completed-future value-set)
    (and url version) (vs/find context url version)
    url (vs/find context url)
    id (find-value-set-by-id db id)
    :else (ac/completed-future (ba/incorrect "Missing ID or URL."))))

(defn- handle-close [stage db]
  (ac/handle
   stage
   (fn [value-set e]
     (let [res (if e (assoc e :t (d/t db)) value-set)]
       (.close ^AutoCloseable db)
       res))))

(defn- context [{:keys [clock sct-release-path]}]
  (if sct-release-path
    (when-ok [sct-context (sct/build-context sct-release-path)]
      {:clock clock :sct/context sct-context})
    {:clock clock}))

(defn- ensure-code-systems
  "Ensures that all code systems of internal terminologies like Snomed CT are
  present in the database node."
  [{:keys [enable-ucum] :as config} {sct-context :sct/context}]
  (when enable-ucum
    @(ucum/ensure-code-system config))
  (when sct-context
    @(sct/ensure-code-systems config sct-context)))

(defn- terminology-service [node context]
  (reify p/TerminologyService
    (p/-code-systems [_]
      (let [db (d/new-batch-db (d/db node))]
        (-> (c/code-systems db)
            (handle-close db))))
    (-code-system-validate-code [_ request]
      (let [db (d/new-batch-db (d/db node))]
        (-> (find-code-system (assoc context :db db) request)
            (ac/then-apply #(cs/validate-code % request))
            (handle-close db))))
    (-expand-value-set [_ request]
      (let [db (d/new-batch-db (d/db node))
            context (assoc context :db db :request request)]
        (-> (find-value-set context)
            (ac/then-compose
             (partial vs-expand/expand-value-set context))
            (handle-close db))))
    (-value-set-validate-code [_ request]
      (let [db (d/new-batch-db (d/db node))
            context (assoc context :db db :request request)]
        (-> (find-value-set context)
            (ac/then-compose
             (partial vs-validate-code/validate-code context))
            (handle-close db))))))

(defmethod m/pre-init-spec ::ts/local [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]
          :opt-un [::enable-ucum ::sct-release-path]))

(defmethod ig/init-key ::ts/local
  [_ {:keys [node] :as config}]
  (log/info "Init local terminology server")
  (if-ok [context (context config)]
    (do (ensure-code-systems config context)
        (terminology-service node context))
    ba/throw-anom))

(derive ::ts/local :blaze/terminology-service)

(ns blaze.operation.patient.purge
  "Main entry point into the Patient $purge operation.

  The resources purged together with the patient are selected by the
  GraphDefinition `PatientPurge` of the Blaze IG, which is installed at startup
  if it doesn't exist."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.permissions :as p]
   [blaze.db.spec]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.luid :as luid]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def ^:private graph-def-resource
  "blaze/operation/patient/purge/GraphDefinition-PatientPurge.json")

(defn- read-graph-def [parsing-context]
  (with-open [in (io/input-stream (io/resource graph-def-resource))]
    (fhir-spec/parse-json parsing-context "GraphDefinition" in)))

(def ^:private access-control-meta
  "Labels a GraphDefinition as internal, so that clients can't change it, and
  tags it read-only, so that an older Blaze after a downgrade still protects
  it.

  The internal label is new, so it exists only in the current system."
  (type/meta
   {:security
    [(type/coding
      {:system (type/uri (canonical/url "CodeSystem/AccessControl"))
       :code #fhir/code "internal"})]
    :tag (canonical/codings "CodeSystem/AccessControl" "read-only")}))

(defn- graph
  "Returns the canonical of `graph-def` in the form `url|version` as used in
  the patient-purge transaction command."
  [{:keys [url version]}]
  (str (:value url) "|" (:value version)))

(defn- graph-def-clauses
  "Returns the search clauses selecting the GraphDefinition with the url and
  version of `graph-def` that Blaze installed itself.

  The `_security` clause keeps a copy a client created from taking the place
  of Blaze's own."
  [{:keys [url version]}]
  [["url" (:value url)]
   ["version" (:value version)]
   ["_security" (str (canonical/url "CodeSystem/AccessControl") "|internal")]])

(defn- graph-def-exists? [db graph-def]
  (do-sync [handles (d/type-query db "GraphDefinition" (graph-def-clauses graph-def))]
    (not (coll/empty? handles))))

(defn- create-graph-def
  "Transacts the creation of `graph-def` on `node`.

  Uses a conditional create, so that two nodes starting at the same time create
  it only once. Uses the permissions of internal code, because the
  GraphDefinition is labelled internal. That is safe, because Blaze ships it
  itself."
  [node context graph-def]
  (let [graph-def (assoc graph-def
                         :id (luid/head (m/luid-generator context))
                         :meta access-control-meta)]
    (d/transact node [[:create graph-def (graph-def-clauses graph-def)]] p/internal)))

(defn- ensure-graph-def
  "Ensures that `graph-def`, selecting the resources to purge, is present in
  the database `node`."
  {:arglists '([context graph-def])}
  [{:keys [node] :as context} graph-def]
  (-> (d/with-db node #(graph-def-exists? % graph-def))
      (ac/then-compose
       (fn [exists?]
         (if exists?
           (ac/completed-future nil)
           (do (log/debug "Create the GraphDefinition" (graph graph-def) "...")
               (create-graph-def node context graph-def)))))))

(defn- diagnostics [id]
  (format "The patient with id `%s` was purged successfully." id))

(defn- handler [{:keys [node]} graph]
  (fn [{{:keys [id]} :path-params}]
    (do-sync [_ (d/transact node [[:patient-purge id graph]])]
      (ring/response
       {:fhir/type :fhir/OperationOutcome
        :issue [{:fhir/type :fhir.OperationOutcome/issue
                 :severity #fhir/code "success"
                 :code #fhir/code "success"
                 :diagnostics (type/string (diagnostics id))}]}))))

(defmethod m/pre-init-spec :blaze.operation.patient/purge [_]
  (s/keys :req-un [:blaze.db/node :blaze.fhir/parsing-context :blaze/clock
                   :blaze/rng-fn]))

(defmethod ig/init-key :blaze.operation.patient/purge
  [_ {:keys [parsing-context] :as context}]
  (log/info "Init FHIR Patient $purge operation handler")
  (let [graph-def (ba/throw-when (read-graph-def parsing-context))]
    @(ensure-graph-def context graph-def)
    (handler context (graph graph-def))))

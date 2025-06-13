(ns blaze.operation.graph
  "Main entry point into the Resource $graph operation.

  See: https://build.fhir.org/resource-operation-graph.html"
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.module :as m]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(def ^:private extension-base
  "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition")

(defn- extension-value [url]
  #(when (= url (:url %)) (type/value (:value %))))

(defn- camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- extension-key [url]
  (let [idx (str/last-index-of url \.)
        name (cond-> url idx (subs (inc idx)))]
    (keyword (camel->kebab name))))

(defn assoc-extension-value [m url extensions]
  (let [value (some (extension-value url) extensions)]
    (cond-> m value (assoc (extension-key url) value))))

(defn- start [{extensions :extension}]
  (some (extension-value (str extension-base ".start")) extensions))

(defn- nodes [{extensions :extension}]
  (into
   []
   (comp
    (filter
     (fn [{:keys [url]}]
       (= (str extension-base ".node") url)))
    (map
     (fn [{extensions :extension}]
       (-> (assoc-extension-value {} "type" extensions)
           (assoc-extension-value "nodeId" extensions)))))
   extensions))

(defn- create-graph [graph-def]
  (let [start (start graph-def)
        nodes (nodes graph-def)]
    {:start-node (some #(when (= start (:node-id %)) %) nodes)}))

(defn- find-graph-def [db uri]
  (do-sync [graph-defs (d/pull-many db (d/type-query db "GraphDefinition" [["url" uri]]))]
    (or (first (fu/sort-by-priority graph-defs))
        (ba/not-found (format "The graph definition `%s` was not found." uri)
                      :http/status 400))))

(defn- handler [context]
  (fn [{:blaze/keys [db]
        {{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params
        {:strs [graph]} :query-params :as request}]
    (if graph
      (let [resource (fhir-util/pull db type id)
            graph-def (find-graph-def db graph)]
        (do-sync [_ (ac/all-of [resource graph-def])]
          (when-let [{:keys [start-node]} (create-graph (ac/join graph-def))]
            (when (= type (:type start-node))
              (ring/response
               {:fhir/type :fhir/Bundle
                :id (m/luid context)
                :type #fhir/code"searchset"
                :total #fhir/unsignedInt 1
                :entry
                [(search-util/entry request (ac/join resource))]})))))
      (ac/completed-future (ba/incorrect "Missing param `graph`")))))

(defmethod m/pre-init-spec :blaze.operation/graph [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.operation/graph [_ context]
  (log/info "Init FHIR Resource $graph operation handler")
  (handler context))

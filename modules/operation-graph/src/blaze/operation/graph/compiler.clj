(ns blaze.operation.graph.compiler
  (:refer-clojure :exclude [compile str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.db.api :as d]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.operation.graph.spec]
   [blaze.util :refer [str]]
   [blaze.util.clauses :as uc]
   [ring.util.codec :as ring-codec]))

(set! *warn-on-reflection* true)

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- link-path-resource-handles [path]
  (fn [db source-resource _target-node]
    (let [[res & more] (fhir-path/eval noop-resolver path source-resource)]
      (when (and (nil? more) (= :fhir/Reference (type/type res)))
        (when-let [ref (:reference res)]
          (when-let [[type id] (fsr/split-literal-ref ref)]
            (ba/map (fhir-util/resource-handle db type id) vector)))))))

(defn- clauses [params]
  (->> (ring-codec/form-decode params)
       (uc/search-clauses)
       (mapv
        (fn [[code value & more :as clause]]
          (if (and (= "{ref}" value) (nil? more))
            (fn [ref]
              [code ref])
            (fn [_ref]
              clause))))))

(defn- link-params-resource-handles [clauses]
  (fn [db {:fhir/keys [type] :keys [id]} target-node]
    (let [ref (str (name type) "/" id)]
      (d/type-query db (:type target-node) (mapv #(% ref) clauses)))))

(defn- link [{:keys [sourceId path targetId params]}]
  (let [link {:source-id (type/value sourceId)
              :target-id (type/value targetId)}]
    (cond
      (and path params)
      (ba/incorrect "Invalid link with path and params.")
      path
      (when-ok [path (fhir-path/compile path)]
        (assoc link :resource-handles (link-path-resource-handles path)))
      params
      (assoc link :resource-handles (link-params-resource-handles (clauses params)))
      :else
      (ba/incorrect "Invalid link without path and params."))))

(def ^:private link-xf
  (comp (map link) (halt-when ba/anomaly?)))

(defn- links [{links :link}]
  (when-ok [links (transduce link-xf conj [] links)]
    (group-by :source-id links)))

(defn- node [{:keys [nodeId type]}]
  (let [id (type/value nodeId)]
    [id {:id id :type (type/value type)}]))

(defn- nodes [nodes]
  (into {} (map node) nodes))

(defn compile
  "Compiles `graph-def` returning a compiled graph."
  [graph-def]
  (when-ok [links (links graph-def)]
    {:start-node-id (type/value (:start graph-def))
     :nodes (nodes (:node graph-def))
     :links links}))

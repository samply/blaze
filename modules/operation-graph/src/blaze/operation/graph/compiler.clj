(ns blaze.operation.graph.compiler
  (:refer-clojure :exclude [compile str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec.references :as fsr]
   [blaze.graph-definition :as gd]
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
    (ac/completed-future
     (let [[res & more] (fhir-path/eval noop-resolver path source-resource)]
       (when (and (nil? more) (= :fhir/Reference (:fhir/type res)))
         (when-let [ref (-> res :reference :value)]
           (when-let [[type id] (fsr/split-literal-ref ref)]
             (ba/map (fhir-util/resource-handle db type id) vector))))))))

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

(defn- resource-handles [{:keys [path params]}]
  (if path
    (link-path-resource-handles path)
    (link-params-resource-handles (clauses params))))

(defn- assoc-resource-handles [link]
  (assoc link :resource-handles (resource-handles link)))

(defn- assoc-links-resource-handles [links]
  (update-vals links #(mapv assoc-resource-handles %)))

(defn compile
  "Compiles `graph-def` returning a compiled graph.

  Each link of the compiled graph has a function under `:resource-handles`
  returning the resource handles of the target node of that link."
  [graph-def]
  (when-ok [graph (gd/compile graph-def)]
    (update graph :links assoc-links-resource-handles)))

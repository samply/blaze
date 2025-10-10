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
   [clojure.string :as str]
   [ring.util.codec :as ring-codec])
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

(defn assoc-extension-value
  ([m url extensions]
   (assoc-extension-value m url (extension-key url) extensions))
  ([m url key extensions]
   (let [value (some (extension-value url) extensions)]
     (cond-> m value (assoc key value)))))

(defn- start [{extensions :extension}]
  (some (extension-value (str extension-base ".start")) extensions))

(defn- nodes [{extensions :extension}]
  (into
   {}
   (comp
    (filter
     (fn [{:keys [url]}]
       (= (str extension-base ".node") url)))
    (map
     (fn [{extensions :extension}]
       (let [id (some (extension-value "nodeId") extensions)]
         [id (assoc-extension-value {:id id} "type" extensions)]))))
   extensions))

(defn- base-link [extensions]
  (-> (assoc-extension-value {} (str extension-base ".link.sourceId") extensions)
      (assoc-extension-value (str extension-base ".link.targetId") extensions)))

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(defn- link-path-resource-handles [path]
  (fn [db source-resource _target-node]
    (let [[res & more] (fhir-path/eval noop-resolver path source-resource)]
      (when (and (nil? more) (= :fhir/Reference (type/type res)))
        (when-let [ref (-> res :reference type/value)]
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

(defn- link [{:keys [path] extensions :extension}]
  (let [link (base-link extensions)
        params (some (extension-value (str extension-base ".link.params")) extensions)]
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

(defn compile
  "Compiles `graph-def` returning a compiled graph."
  [graph-def]
  (when-ok [links (links graph-def)]
    {:start-node-id (start graph-def)
     :nodes (nodes graph-def)
     :links links}))

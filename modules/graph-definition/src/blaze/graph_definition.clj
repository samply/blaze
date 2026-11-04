(ns blaze.graph-definition
  "Compiles GraphDefinition resources into plain data.

  Blaze uses the R5 structure of GraphDefinition through cross-version
  extensions in R4."
  (:refer-clojure :exclude [compile str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir-path :as fhir-path]
   [blaze.util :refer [str]]
   [clojure.string :as str])
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(def ^:private extension-base
  "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition")

(defn- extension-value [url]
  #(when (= url (:url %)) (-> % :value :value)))

(defn- camel->kebab [s]
  (.to CaseFormat/LOWER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn- extension-key [url]
  (let [idx (str/last-index-of url \.)
        name (cond-> url idx (subs (inc idx)))]
    (keyword (camel->kebab name))))

(defn- assoc-extension-value [m url extensions]
  (let [value (some (extension-value url) extensions)]
    (cond-> m value (assoc (extension-key url) value))))

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

(defn- link [{{path :value} :path extensions :extension}]
  (let [link (base-link extensions)
        params (some (extension-value (str extension-base ".link.params")) extensions)]
    (cond
      (and path params)
      (ba/incorrect "Invalid link with path and params.")
      path
      (when-ok [path (fhir-path/compile path)]
        (assoc link :path path))
      params
      (assoc link :params params)
      :else
      (ba/incorrect "Invalid link without path and params."))))

(def ^:private link-xf
  (comp (map link) (halt-when ba/anomaly?)))

(defn- links [{links :link}]
  (when-ok [links (transduce link-xf conj [] links)]
    (group-by :source-id links)))

(defn compile
  "Compiles `graph-def` into a map of the `:start-node-id`, the `:nodes` by id
  and the `:links` by source node id.

  Each link has either a compiled FHIRPath `:path` or the `:params` string of a
  reverse lookup.

  Returns an anomaly if `graph-def` is invalid."
  [graph-def]
  (when-ok [links (links graph-def)]
    {:start-node-id (start graph-def)
     :nodes (nodes graph-def)
     :links links}))

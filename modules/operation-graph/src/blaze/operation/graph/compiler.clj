(ns blaze.operation.graph.compiler
  (:refer-clojure :exclude [compile str])
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec.references :as fsr]
   [blaze.graph-definition :as gd]
   [blaze.operation.graph.spec]
   [blaze.util :refer [str]]
   [blaze.value-set-expander :as vse]))

(set! *warn-on-reflection* true)

(def ^:private noop-resolver
  (reify fhir-path/Resolver (-resolve [_ _])))

(def ^:private non-domain-resource-types
  "The resource types which don't inherit from `DomainResource`."
  #{"Binary" "Bundle" "Parameters"})

(defn node-type-matches?
  "Returns true if a resource of `type` can be the resource of a node with
  `node-type`, which can be one of the abstract types `Resource` and
  `DomainResource`."
  [node-type type]
  (case node-type
    "Resource" true
    "DomainResource" (not (contains? non-domain-resource-types type))
    (= node-type type)))

(defn- literal-ref
  "Returns the type and id of the literal reference of `x` if it's a Reference."
  [x]
  (when (identical? :fhir/Reference (:fhir/type x))
    (some-> (:reference x) :value fsr/split-literal-ref)))

(defn- existing-resource-handle
  "Returns the resource handle of the resource with `type` and `id` or nil if
  it doesn't exist or is deleted."
  [db [type id]]
  (when-let [handle (d/resource-handle db type id)]
    (when-not (d/deleted? handle)
      handle)))

(defn- reference-resource-handles
  "Returns a transducer mapping values to the resource handles of resources
  with a type matching `node-type`, they refer to."
  [db node-type]
  (comp (keep literal-ref)
        (filter (comp (partial node-type-matches? node-type) first))
        (keep (partial existing-resource-handle db))))

(defn- link-path-resource-handles [path]
  (fn [db source-resource {node-type :type}]
    (when-ok [values (fhir-path/eval noop-resolver path source-resource)]
      (into [] (reference-resource-handles db node-type) values))))

(defn- clause-fn
  "Returns a function of the reference to the source resource returning
  `clause` with that reference if it's a ref clause."
  [[code :as clause]]
  (if (gd/ref-clause? clause)
    (fn [ref]
      [code ref])
    (fn [_ref]
      clause)))

(defn- link-params-resource-handles [clauses]
  (fn [db {:fhir/keys [type] :keys [id]} target-node]
    (let [ref (str (name type) "/" id)]
      (d/type-query db (:type target-node) (mapv #(% ref) clauses)))))

(defn- no-resource-handles [_db _source-resource _target-node]
  [])

(defn- resource-handles [{:keys [path clauses]}]
  (if path
    (link-path-resource-handles path)
    (link-params-resource-handles (mapv clause-fn clauses))))

(defn- assoc-resource-handles [link]
  (assoc link :resource-handles (resource-handles link)))

(defn- assoc-links-resource-handles [links]
  (update-vals links #(mapv assoc-resource-handles %)))

(defn- unsatisfiable-link
  "Returns `link` with resource handles that are always empty, because its link
  params can't match any resource, as a value set expands to no codes."
  [link]
  (assoc link :resource-handles no-resource-handles))

(defn- expand-link-value-sets
  [value-set-expander nodes {:keys [clauses target-id] :as link}]
  (if clauses
    (do-sync [clauses (vse/expand-clauses value-set-expander (:type (nodes target-id)) clauses)]
      (if clauses
        (assoc-resource-handles (assoc link :clauses clauses))
        (unsatisfiable-link link)))
    (ac/completed-future link)))

(defn- expand-links-value-sets [value-set-expander nodes links]
  (ac/all-values (mapv (partial expand-link-value-sets value-set-expander nodes) links)))

(defn expand-value-sets
  "Returns a CompletableFuture that will complete with `graph` whose link params
  have their value sets expanded (see `vse/expand-clauses`), or will complete
  exceptionally if a value set can't be expanded.

  The expansion is done once for `graph`, so that all source resources use the
  same expansion."
  [value-set-expander {:keys [nodes links] :as graph}]
  (let [source-ids (vec (keys links))]
    (do-sync [expanded-links (ac/all-values
                              (mapv #(expand-links-value-sets value-set-expander nodes (links %))
                                    source-ids))]
      (assoc graph :links (zipmap source-ids expanded-links)))))

(defn compile
  "Compiles `graph-def` returning a compiled graph.

  Each link of the compiled graph has a function under `:resource-handles`
  returning the resource handles of the target node of that link or an
  anomaly."
  [graph-def]
  (when-ok [graph (gd/compile graph-def)]
    (update graph :links assoc-links-resource-handles)))

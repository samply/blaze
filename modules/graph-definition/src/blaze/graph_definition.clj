(ns blaze.graph-definition
  "Compiles GraphDefinition resources into plain data.

  Blaze uses the R5 structure of GraphDefinition through cross-version
  extensions in R4."
  (:refer-clojure :exclude [compile str])
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.search-clauses :as fsc]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [ring.util.codec :as ring-codec]))

(set! *warn-on-reflection* true)

(def ^:private extension-base
  "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition")

(def ^:private start-url (str extension-base ".start"))
(def ^:private node-url (str extension-base ".node"))
(def ^:private link-params-url (str extension-base ".link.params"))
(def ^:private link-compartment-url (str extension-base ".link.compartment"))

(defn- url= [url]
  #(= url (:url %)))

(defn- extension-value [url]
  #(when (= url (:url %)) (-> % :value :value)))

(defn- start-node-id [{extensions :extension} nodes]
  (if-let [id (some (extension-value start-url) extensions)]
    (if (contains? nodes id)
      id
      (ba/incorrect (format "Invalid start node id `%s` not referring to a node." id)))
    (ba/incorrect "Invalid GraphDefinition without start node.")))

(defn- check-start-type
  "Returns `start-node-id` if the R4 element `start` of `graph-def` has no value
  or equals the type of the start node, or an anomaly otherwise.

  The element `start` holds the type of the start node in R4. It is searchable
  by the search param `start`, so it has to agree with the start node."
  {:arglists '([graph-def nodes start-node-id])}
  [{{start-type :value} :start} nodes start-node-id]
  (let [{node-type :type} (nodes start-node-id)]
    (if (or (nil? start-type) (= start-type node-type))
      start-node-id
      (ba/incorrect
       (format "Invalid start type `%s` not matching the type `%s` of the start node `%s`."
               start-type node-type start-node-id)))))

(def ^:private abstract-resource-types
  "The abstract resource types which `fhir-spec/resource-type?` doesn't cover,
  but which are allowed as node type, e.g. for polymorphic references."
  #{"Resource" "DomainResource"})

(defn- node-type? [type]
  (or (fhir-spec/resource-type? type) (contains? abstract-resource-types type)))

(defn- node [{extensions :extension}]
  (let [id (some (extension-value "nodeId") extensions)
        type (some (extension-value "type") extensions)]
    (cond
      (nil? id)
      (ba/incorrect "Invalid node without nodeId.")

      (nil? type)
      (ba/incorrect (format "Invalid node `%s` without type." id))

      (not (node-type? type))
      (ba/incorrect (format "Invalid node `%s` with unknown resource type `%s`." id type))

      (some (url= "profile") extensions)
      (ba/incorrect (format "Invalid node `%s` with unsupported profile." id))

      :else
      {:id id :type type})))

(defn- add-node [nodes {:keys [id] :as node}]
  (if (contains? nodes id)
    (reduced (ba/incorrect (format "Invalid node `%s` with duplicate nodeId." id)))
    (assoc nodes id node)))

(def ^:private node-xf
  (comp (filter (url= node-url)) (map node) (halt-when ba/anomaly?)))

(defn- nodes [{extensions :extension}]
  (transduce node-xf (completing add-node) {} extensions))

(defn- link-node-id
  "Returns the id of the node `name` (`sourceId` or `targetId`) of the link with
  `extensions` refers to or an anomaly if it's missing or doesn't refer to one
  of `nodes`."
  [nodes name extensions]
  (if-let [id (some (extension-value (str extension-base ".link." name)) extensions)]
    (if (contains? nodes id)
      id
      (ba/incorrect (format "Invalid link with %s `%s` not referring to a node." name id)))
    (ba/incorrect (format "Invalid link without %s." name))))

(defn ref-clause?
  "Returns true if `clause` is a search clause with the single value `{ref}`
  which has to be replaced by the reference to the source resource."
  [[_code value & more]]
  (and (= "{ref}" value) (nil? more)))

(defn- contains-ref?
  "Returns true if one of the values of `clause` is `{ref}`."
  [[_code & values]]
  (boolean (some #{"{ref}"} values)))

(defn- params-link
  "Returns `link` with the search clauses of `params`, or an anomaly if the
  `target-node` has an abstract type or not exactly one clause has the single
  value `{ref}`.

  The reverse lookup of the link needs the concrete type of the target node.
  Without a clause with the value `{ref}`, the link would select all resources
  of the target type matching the remaining clauses, not only those linked to
  the source resource. Only one such clause is supported for now."
  [link {:keys [id type] :as _target-node} params]
  (if (contains? abstract-resource-types type)
    (ba/incorrect (format "Invalid link with params to node `%s` of abstract type `%s`." id type))
    (let [clauses (-> params ring-codec/form-decode-map fsc/search-clauses)
          [ref-clause more] (filter contains-ref? clauses)]
      (cond
        (nil? ref-clause)
        (ba/incorrect (format "Invalid link with params `%s` without a parameter with the value `{ref}`." params))

        more
        (ba/incorrect (format "Invalid link with params `%s` with more than one parameter with the value `{ref}`." params))

        (not (ref-clause? ref-clause))
        (ba/incorrect (format "Invalid link with params `%s` with the value `{ref}` combined with other values." params))

        :else
        (assoc link :clauses clauses)))))

(defn- link [nodes {{path :value} :path extensions :extension}]
  (when-ok [source-id (link-node-id nodes "sourceId" extensions)
            target-id (link-node-id nodes "targetId" extensions)]
    (let [link {:source-id source-id :target-id target-id}
          params (some (extension-value link-params-url) extensions)]
      (cond
        (some (url= link-compartment-url) extensions)
        (ba/incorrect "Invalid link with unsupported compartment.")

        (and path params)
        (ba/incorrect "Invalid link with path and params.")

        path
        (when-ok [path (fhir-path/compile path)]
          (assoc link :path path))

        params
        (params-link link (nodes target-id) params)

        :else
        (ba/incorrect "Invalid link without path and params.")))))

(defn- link-xf [nodes]
  (comp (map (partial link nodes)) (halt-when ba/anomaly?)))

(defn- links [nodes {links :link}]
  (when-ok [links (transduce (link-xf nodes) conj [] links)]
    (group-by :source-id links)))

(defn compile
  "Compiles `graph-def` into a map of the `:start-node-id`, the `:nodes` by id
  and the `:links` by source node id.

  Each link has either a compiled FHIRPath `:path` or the search `:clauses` of
  a reverse lookup. Exactly one of the clauses has the single value `{ref}`
  (see `ref-clause?`) which identifies the source resource.

  Returns an anomaly if `graph-def` is invalid. That is the case if:
   * the start node is missing or doesn't refer to a node
   * the R4 element `start` has a value other than the type of the start node
   * a node has no `nodeId`, no `type` or a type that isn't a resource type or
     one of the abstract types `Resource` and `DomainResource`
   * a `nodeId` is used more than once
   * a link has no `sourceId` or `targetId` or they don't refer to a node
   * a link has both or neither a path and params
   * a link has params but its target node has an abstract type
   * the params of a link have not exactly one parameter with the value
     `{ref}` or `{ref}` is combined with other values
   * the path of a link is no valid FHIRPath expression
   * a node has a profile or a link has compartment rules, because they are
     not supported

  The elements `min` and `max` of links are ignored."
  [graph-def]
  (when-ok [nodes (nodes graph-def)
            start-node-id (start-node-id graph-def nodes)
            start-node-id (check-start-type graph-def nodes start-node-id)
            links (links nodes graph-def)]
    {:start-node-id start-node-id
     :nodes nodes
     :links links}))

(defn- rev-link
  "Returns the `[type code]` tuple of `link` or an anomaly if `link` isn't a
  single reverse lookup from the start node.

  The `start-node` has to be of type Patient. A link to another node of type
  Patient is rejected, because such a node stands for other patients."
  {:arglists '([nodes start-node link])}
  [nodes {start-node-id :id start-type :type} {:keys [source-id target-id path]
                                               [[code :as clause] & more] :clauses}]
  (let [{target-type :type} (nodes target-id)]
    (cond
      (not= start-node-id source-id)
      (ba/incorrect (format "Invalid link from node `%s` instead of the start node `%s`." source-id start-node-id))

      path
      (ba/incorrect (format "Invalid link to node `%s` with path instead of params." target-id))

      (or more (not (ref-clause? clause)))
      (ba/incorrect (format "Invalid link to node `%s` with params other than a single parameter with the value `{ref}`." target-id))

      (str/includes? code ":")
      (ba/incorrect (format "Invalid link to node `%s` with the parameter `%s` having a modifier." target-id code))

      (= start-type target-type)
      (ba/incorrect (format "Invalid link to node `%s` of type `%s`, the type of the start node." target-id target-type))

      (contains? abstract-resource-types target-type)
      (ba/incorrect (format "Invalid link to node `%s` of abstract type `%s`." target-id target-type))

      :else
      [target-type code])))

(defn- rev-link-xf [nodes start-node]
  (comp (mapcat val) (map (partial rev-link nodes start-node))
        (halt-when ba/anomaly?) (distinct)))

(defn rev-links
  "Returns the `[type code]` tuples of the links of `compiled-graph`, a graph
  compiled by `compile`, in order to select the resources of `type` that
  reference the start resource through the reference search param with `code`.

  Returns an anomaly if the graph is outside the restricted subset of one hop
  reverse lookups from a Patient. That is the case if:
   * the start node isn't of type Patient
   * a link doesn't start at the start node
   * a link has a path instead of params
   * the params of a link aren't the single parameter `<code>={ref}`
   * the parameter of a link has a modifier
   * a link ends at another node of type Patient
   * a link ends at a node of an abstract type"
  {:arglists '([compiled-graph])}
  [{:keys [start-node-id nodes links]}]
  (let [{:keys [type] :as start-node} (nodes start-node-id)]
    (if (= "Patient" type)
      (transduce (rev-link-xf nodes start-node) conj [] links)
      (ba/incorrect (format "Invalid start node `%s` of type `%s` instead of `Patient`." start-node-id type)))))

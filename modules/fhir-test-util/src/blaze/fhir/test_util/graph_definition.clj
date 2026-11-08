(ns blaze.fhir.test-util.graph-definition
  "Helpers to build GraphDefinition resources in tests.

  Blaze uses the R5 structure of GraphDefinition through cross-version
  extensions."
  (:require
   [blaze.fhir.spec.type :as type]))

(def data-absent-reason-unsupported
  #fhir/Extension
   {:url "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
    :value #fhir/code "unsupported"})

(def extension-base
  "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition")

(defn extension-start [& {:as kvs}]
  (type/extension (assoc kvs :url (str extension-base ".start"))))

(defn extension-node [& {:as kvs}]
  (type/extension (assoc kvs :url (str extension-base ".node"))))

(defn extension-link-source-id [& {:as kvs}]
  (type/extension (assoc kvs :url (str extension-base ".link.sourceId"))))

(defn extension-link-target-id [& {:as kvs}]
  (type/extension (assoc kvs :url (str extension-base ".link.targetId"))))

(defn extension-link-params [& {:as kvs}]
  (type/extension (assoc kvs :url (str extension-base ".link.params"))))

(defn extension-link-compartment [& {:as kvs}]
  (type/extension (assoc kvs :url (str extension-base ".link.compartment"))))

(defn graph-def
  "Creates a GraphDefinition with the start node `patient` unless `:start` is
  given. A `:start` of nil omits the start node.

  The R4 element `start` holds the type of the start node. It is data absent
  unless `:start-type` is given.

  The `:nodes`, created by `node`, and the `:link`s, created by `link`, are
  optional."
  [& {:keys [start-type nodes link] :as opts}]
  (let [start (get opts :start "patient")]
    (cond->
     {:fhir/type :fhir/GraphDefinition :id "0"
      :extension
      (cond-> []
        start (conj (extension-start :value (type/id start)))
        true (into nodes))
      :url #fhir/uri "144200"
      :name #fhir/string "test"
      :status #fhir/code "active"
      :start (if start-type
               (type/code start-type)
               (type/code {:extension [data-absent-reason-unsupported]}))}
      link (assoc :link link))))

(defn node
  "Creates a node extension with `id` and `type`. Omits `id` or `type` if nil."
  [id type & {:keys [profile]}]
  (extension-node
   :extension
   (cond-> []
     id (conj (type/extension {:url "nodeId" :value (type/id id)}))
     type (conj (type/extension {:url "type" :value (type/code type)}))
     profile (conj (type/extension {:url "profile" :value (type/canonical profile)})))))

(defn link
  "Creates a link from the node with `source-id` to the node with `target-id`.
  Omits `source-id` or `target-id` if nil.

  The link either has search `:params` or a FHIRPath `:path`. A `:compartment`
  adds a Patient compartment rule."
  [source-id target-id & {:keys [params path compartment]}]
  (cond->
   {:fhir/type :fhir.GraphDefinition/link
    :extension
    (cond-> []
      source-id (conj (extension-link-source-id :value (type/id source-id)))
      target-id (conj (extension-link-target-id :value (type/id target-id)))
      params (conj (extension-link-params :value (type/string params)))
      compartment (conj (extension-link-compartment
                         :extension
                         [(type/extension {:url "use" :value #fhir/code "where"})
                          (type/extension {:url "rule" :value #fhir/code "identical"})
                          (type/extension {:url "code" :value #fhir/code "Patient"})])))}
    path (assoc :path (type/string path))))

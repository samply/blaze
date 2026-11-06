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

(ns blaze.fhir.structure-definition-repo
  (:require
   [blaze.fhir.spec.impl :as si]
   [blaze.fhir.structure-definition-repo.protocols :as p]
   [blaze.fhir.structure-definition-repo.spec]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [taoensso.timbre :as log]))

(defn primitive-types
  "Returns a list of all StructureDefinition resources of FHIR R4 primitive
  types.

  Loads them from directly classpath. Doesn't store them in memory."
  [repo]
  (p/-primitive-types repo))

(defn complex-types
  "Returns a list of all StructureDefinition resources of FHIR R4 complex types.

  Loads them from directly classpath. Doesn't store them in memory."
  [repo]
  (p/-complex-types repo))

(defn resources
  "Returns a list of all StructureDefinition resources of FHIR R4 resources.

  Loads them from directly classpath. Doesn't store them in memory."
  [repo]
  (p/-resources repo))

(defn- single-type-paths
  [type-code elements]
  (keep
   (fn [{:keys [path type]}]
     (when (and (= 1 (count type))
                (= type-code (:code (first type))))
       path))
   elements))

(defn code-expressions
  "Returns a set of FHIRPath expressions that evaluate to a primitive code type
  on R4 resources."
  [repo]
  (into
   #{}
   (mapcat (comp (partial single-type-paths "code") :element :snapshot))
   (resources repo)))

(defn- single-type-code [element]
  (let [types (:type element)]
    (when (= 1 (count types))
      (:code (first types)))))

(defn- publication-status-binding? [element]
  (str/includes? (get-in element [:binding :valueSet] "") "publication-status"))

(defn canonical-url-expressions
  "Returns a set of element paths whose single type is `uri`, whose resource has
  both a `.version` element and a `.status` element bound to the
  `publication-status` value set (the FHIR canonical/metadata-resource pattern).

  These are the `.url` paths of conformance and knowledge resources, e.g.
  `CapabilityStatement.url`, `ValueSet.url`."
  [repo]
  (into
   #{}
   (keep
    (fn [{:keys [id snapshot]}]
      (let [by-path (into {} (map (juxt :path identity)) (:element snapshot))
            url-el  (get by-path (str id ".url"))
            st-el   (get by-path (str id ".status"))]
        (when (and url-el
                   (= "uri" (single-type-code url-el))
                   (get by-path (str id ".version"))
                   (publication-status-binding? st-el))
          (str id ".url")))))
   (resources repo)))

(defn- complex-type-canonical-sub-paths
  [repo]
  (into
   {}
   (keep
    (fn [{:keys [id snapshot]}]
      (let [canonical-paths (single-type-paths "canonical" (:element snapshot))]
        (when (seq canonical-paths)
          [id (mapv (fn [p] (subs p (inc (count id)))) canonical-paths)]))))
   (complex-types repo)))

(defn- register-all!
  "Register specs for all FHIR data types and resources.

  These specs are used to conform and unform resources from JSON/XML to the
  internal format."
  [repo]
  (log/trace "Register primitive types")
  (si/register (mapcat si/primitive-type->spec-defs (primitive-types repo)))

  (log/trace "Register complex types")
  (si/register (mapcat si/struct-def->spec-def (complex-types repo)))

  (log/trace "Register resources")
  (si/register (mapcat si/struct-def->spec-def (resources repo))))

(def ^:private object-mapper
  (j/object-mapper
   {:decode-key-fn true}))

(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (log/trace (format "Read FHIR bundle `%s`." resource-name))
  (with-open [rdr (io/reader (io/resource resource-name))]
    (j/read-value rdr object-mapper)))

(defn- abstract-base-resources
  []
  (filter (comp #{"Resource" "DomainResource"} :id)
          (map :resource
               (:entry (read-bundle "blaze/fhir/4.0.1/profiles-resources.json")))))

(defn canonical-expressions
  "Returns a set of FHIRPath expressions that evaluate to a primitive canonical type
  on R4 resources, including nested paths through complex types."
  [repo]
  (let [complex-canonical (complex-type-canonical-sub-paths repo)
        all-resources (concat (resources repo) (abstract-base-resources))]
    (into
     #{}
     (mapcat
      (fn [{:keys [snapshot]}]
        (mapcat
         (fn [{:keys [path type]}]
           (if (and (= 1 (count type))
                    (= "canonical" (:code (first type))))
             [path]
             (when-let [sub-paths (get complex-canonical (:code (first type)))]
               (map #(str path "." %) sub-paths))))
         (:element snapshot))))
     all-resources)))

(def ^:private repo
  (reify p/StructureDefinitionRepo
    (-primitive-types [_]
      (into
       []
       (comp (map :resource)
             (filter (comp #{"primitive-type"} :kind)))
       (:entry (read-bundle "blaze/fhir/4.0.1/profiles-types.json"))))
    (-complex-types [_]
      (into
       []
       (comp (map :resource)
             (filter (comp #{"complex-type"} :kind))
             (remove :abstract)
             (remove (comp #{"constraint"} :derivation)))
       (:entry (read-bundle "blaze/fhir/4.0.1/profiles-types.json"))))
    (-resources [_]
      (into
       []
       (comp (map :resource)
             (filter (comp #{"resource"} :kind))
             (remove :abstract)
             (remove :experimental))
       (:entry (read-bundle "blaze/fhir/4.0.1/profiles-resources.json"))))))

(defmethod ig/init-key :blaze.fhir/structure-definition-repo
  [_ _]
  (log/info "Init structure definition repository")
  (register-all! repo)
  repo)

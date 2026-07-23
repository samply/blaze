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

(defn- read-bundle
  "Reads a bundle from classpath named `resource-name`."
  [resource-name]
  (log/trace (format "Read FHIR bundle `%s`." resource-name))
  (with-open [rdr (io/reader (io/resource resource-name))]
    (j/read-value rdr j/keyword-keys-object-mapper)))

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

(defn- single-type-code [element]
  (let [types (:type element)]
    (when (= 1 (count types))
      (:code (first types)))))

(defn- publication-status-binding? [element]
  (str/includes? (get-in element [:binding :valueSet] "") "publication-status"))

(defn- canonical-url-paths
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
            url-path (str id ".url")
            url-el (get by-path url-path)
            ;; every? boolean guards against nil from missing elements
            ;; or unexpected types: all four conditions must be truthy
            ;; for the url-path to be a canonical-url.
            canonical? (every? boolean
                               [url-el
                                (= "uri" (single-type-code url-el))
                                (get by-path (str id ".version"))
                                (publication-status-binding? (get by-path (str id ".status")))])]
        (when canonical? url-path))))
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

(defn- abstract-base-resources
  []
  (filter (comp #{"Resource" "DomainResource"} :id)
          (map :resource
               (:entry (read-bundle "blaze/fhir/4.0.1/profiles-resources.json")))))

(defn- element-kind
  "Returns the kind keyword (:code, :canonical, or :canonical-url) for an
  element based on its path and type in the canonical-url set."
  [canonical-url-set {:keys [path] :as element}]
  (or (when (contains? canonical-url-set path) :canonical-url)
      (case (single-type-code element)
        "code" :code
        "canonical" :canonical
        nil)))

(defn- element-canonical-sub-paths
  "Returns canonical sub-path entries for an element if its type is a complex
  type with canonical sub-paths."
  [complex-canonical {:keys [path type]}]
  (when-let [sub-paths (get complex-canonical (:code (first type)))]
    (map (fn [sub] [(str path "." sub) :canonical])
         sub-paths)))

(defn- resource-element-entries
  "Returns all categorized [path kind] entries for a resource."
  [canonical-url-set complex-canonical resource include-direct?]
  (mapcat
   (fn [el]
     (concat
      (when include-direct?
        (when-let [kind (element-kind canonical-url-set el)]
          [[(:path el) kind]]))
      (element-canonical-sub-paths complex-canonical el)))
   (:element (:snapshot resource))))

(defn expression-types
  "Returns a map from FHIRPath expression to a keyword categorizing the
  expression's static result type on R4 resources.

  Categories are
    :code - primitive `code` type (e.g. `Observation.status`)
    :canonical - primitive `canonical` type (e.g. `Resource.meta.profile`)
    :canonical-url - `.url` of a conformance/knowledge resource
                     (e.g. `ValueSet.url`), companion to `.version` indexing

  Paths not in any of the categories above are absent from the map."
  [repo]
  (let [canonical-url-set (canonical-url-paths repo)
        complex-canonical (complex-type-canonical-sub-paths repo)]
    (into {}
          (concat
           (mapcat #(resource-element-entries canonical-url-set complex-canonical % true)
                   (resources repo))
           (mapcat #(resource-element-entries canonical-url-set complex-canonical % false)
                   (abstract-base-resources))))))

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

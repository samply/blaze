(ns blaze.fhir.structure-definition-repo
  (:require
   [blaze.fhir.spec.impl :as si]
   [blaze.fhir.structure-definition-repo.protocols :as p]
   [blaze.fhir.structure-definition-repo.spec]
   [clojure.java.io :as io]
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

(defn code-expressions
  "Returns a set of FHIRPath expressions that evaluate to a primitive code type
  on R4 resources."
  [repo]
  (into
   #{}
   (mapcat
    (fn [{:keys [snapshot]}]
      (keep
       (fn [{:keys [path type]}]
         (when (and (= 1 (count type))
                    (= "code" (:code (first type))))
           path))
       (:element snapshot))))
   (resources repo)))

(defn- register-all!
  "Register specs for all FHIR data types and resources.

  That specs are used to conform and unform resources from JSON/XML to the
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

(def ^:private repo
  (reify p/StructureDefinitionRepo
    (-primitive-types [_]
      (into
       []
       (comp (map :resource)
             (filter (comp #{"primitive-type"} :kind)))
       (:entry (read-bundle "blaze/fhir/r4/profiles-types.json"))))
    (-complex-types [_]
      (into
       []
       (comp (map :resource)
             (filter (comp #{"complex-type"} :kind))
             (remove :abstract)
             (remove (comp #{"constraint"} :derivation)))
       (:entry (read-bundle "blaze/fhir/r4/profiles-types.json"))))
    (-resources [_]
      (into
       []
       (comp (map :resource)
             (filter (comp #{"resource"} :kind))
             (remove :abstract)
             (remove :experimental))
       (:entry (read-bundle "blaze/fhir/r4/profiles-resources.json"))))))

(defmethod ig/init-key :blaze.fhir/structure-definition-repo
  [_ _]
  (log/info "Init structure definition repository")
  (register-all! repo)
  repo)

(ns blaze.fhir.structure-definition-repo
  (:require
   [blaze.fhir.spec.impl :as si]
   [blaze.fhir.structure-definition-repo.protocols :as p]
   [blaze.fhir.structure-definition-repo.spec]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [taoensso.timbre :as log]))

(defn primitive-types [repo]
  (p/-primitive-types repo))

(defn complex-types [repo]
  (p/-complex-types repo))

(defn resources [repo]
  (p/-resources repo))

(defn- register-all! [repo]
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
             ;; TODO: look into how to handle this special quantity types
             (remove (comp #{"MoneyQuantity" "SimpleQuantity"} :name)))
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

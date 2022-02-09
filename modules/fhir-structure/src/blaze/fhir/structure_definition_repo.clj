(ns blaze.fhir.structure-definition-repo
  (:require
    [blaze.fhir.spec.impl :as si]
    [blaze.fhir.structure-definition-repo.impl :as impl]
    [blaze.fhir.structure-definition-repo.protocols :as p]
    [blaze.fhir.structure-definition-repo.spec]
    [integrant.core :as ig]
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


(def ^:private repo
  (reify p/StructureDefinitionRepo
    (-primitive-types [_]
      (filterv (comp #{"primitive-type"} :kind) (impl/data-types)))
    (-complex-types [_]
      (into
        []
        (comp
          (filter (comp #{"complex-type"} :kind))
          (remove :abstract)
          ;; TODO: look into how to handle this special quantity types
          (remove (comp #{"MoneyQuantity" "SimpleQuantity"} :name)))
        (impl/data-types)))
    (-resources [_]
      (into
        []
        (comp (filter (comp #{"resource"} :kind))
              (remove :abstract)
              (remove :experimental))
        (impl/resources)))))


(defmethod ig/init-key :blaze.fhir/structure-definition-repo
  [_ _]
  (log/info "Init structure definition repository")
  (register-all! repo)
  repo)

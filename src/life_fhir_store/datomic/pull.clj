(ns life-fhir-store.datomic.pull
  "Create Pull Patterns from FHIR Structure Definitions"
  (:require
    [clojure.spec.alpha :as s]
    [life-fhir-store.spec]
    [life-fhir-store.structure-definition :as sd]))


(declare pull-pattern)


(s/fdef element-pull-pattern
  :args (s/cat :context (s/map-of string? :life/structure-definition)
               :element-definition :life/element-definition))

(defn- element-pull-pattern
  {:arglists '([context element-definition])}
  [context
   {:life.element-definition/keys [key]
    {type-code :life.element-definition.type/code}
    :life.element-definition/type
    :as element-definition}]
  (if (sd/primitive-type? element-definition)
    [key]
    (when (not (#{"Reference"} type-code))
      (when-let [structure-definition (get context type-code)]
        [{key (pull-pattern context structure-definition)}]))))


(s/fdef pull-pattern
  :args (s/cat :context (s/map-of string? :life/structure-definition)
               :structure-definition :life/structure-definition))

(defn pull-pattern
  {:arglists '([context structure-definition])}
  [context {:life.structure-definition/keys [elements]}]
  (into
    []
    (comp (filter :life.element-definition/summary?)
          (mapcat (partial element-pull-pattern context)))
    (vals elements)))

(comment
  (def def (life-fhir-store.util/structure-definitions "Observation"))
  (pull-pattern life-fhir-store.util/structure-definitions def)
  (clojure.repl/pst)
  )

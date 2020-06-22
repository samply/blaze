(ns blaze.db.tx-log.local.references
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [clojure.spec.alpha :as s]))


(defn- extract-references*
  [m spec]
  (let [child-specs (fhir-spec/child-specs spec)]
    (reduce
      (fn [references [key val]]
        (if-let [spec (get child-specs key)]
          (cond
            (identical? :many (fhir-spec/cardinality spec))
            (let [spec (fhir-spec/type-spec spec)]
              (if (or (fhir-spec/primitive? spec) (fhir-spec/system? spec))
                references
                (reduce
                  (fn [ret val]
                    (into ret (extract-references* val spec)))
                  references
                  val)))

            (identical? :fhir/Reference spec)
            (if-let [reference (:reference val)]
              (let [res (s/conform :blaze.fhir/local-ref reference)]
                (if (s/invalid? res)
                  references
                  (conj references res)))
              references)

            (or (fhir-spec/primitive? spec) (fhir-spec/system? spec))
            references

            (identical? :fhir/Resource spec)
            (into references (extract-references* val (keyword "fhir" (:resourceType val))))

            :else
            (into references (extract-references* val spec)))
          references))
      []
      m)))


(defn extract-references [{type :resourceType :as resource}]
  (extract-references* resource (keyword "fhir" type)))

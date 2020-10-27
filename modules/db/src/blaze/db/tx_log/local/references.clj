(ns blaze.db.tx-log.local.references
  (:require
    [blaze.fhir.spec :as fhir-spec]
    [clojure.spec.alpha :as s]))


(defn- extract-references* [m]
  (reduce
    (fn [references [_ val]]
      (cond
        (sequential? val)
        (reduce
          (fn [ret val]
            (if (fhir-spec/primitive-val? val)
              ret
              (into ret (extract-references* val))))
          references
          val)

        (identical? :fhir/Reference (fhir-spec/fhir-type val))
        (if-let [reference (:reference val)]
          (let [res (s/conform :blaze.fhir/local-ref reference)]
            (if (s/invalid? res)
              references
              (conj references res)))
          references)

        (fhir-spec/primitive-val? val)
        references

        :else
        (into references (extract-references* val))))
    []
    (dissoc m :fhir/type)))


(defn extract-references [resource]
  (extract-references* resource))

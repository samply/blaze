(ns blaze.elm.resource
  (:require
   [blaze.db.api :as d]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.spec])
  (:import
   [clojure.lang IKeywordLookup ILookup ILookupThunk]))

(set! *warn-on-reflection* true)

;; A resource that is a wrapper of a resource-handle that will lazily pull the
;; resource content if some property other than :id is accessed.
(deftype Resource [db handle ^long lastChangeT content]
  ILookup
  (valAt [r key]
    (.valAt r key nil))
  (valAt [_ key not-found]
    (case key
      :fhir/type (:fhir/type handle)
      :id (:id handle)
      (-> (or @content (vreset! content @(d/pull-content db handle)))
          (get key not-found))))

  IKeywordLookup
  (getLookupThunk [_ key]
    (case key
      :fhir/type
      (reify ILookupThunk
        (get [thunk target]
          (if (instance? Resource target)
            (:fhir/type (.handle ^Resource target))
            thunk)))
      :id
      (reify ILookupThunk
        (get [thunk target]
          (if (instance? Resource target)
            (:id (.handle ^Resource target))
            thunk)))
      nil))

  core/Expression
  (-static [_]
    true)
  (-attach-cache [expr _]
    [(fn [] [expr])])
  (-patient-count [_]
    nil)
  (-resolve-refs [expr _]
    expr)
  (-resolve-params [expr _]
    expr)
  (-optimize [expr _]
    expr)
  (-eval [expr _ _ _]
    expr)
  (-form [_]
    (list 'resource (name (:fhir/type handle)) (:id handle) (:t handle)))

  Object
  (toString [_]
    (str (name (:fhir/type handle)) "[id = " (:id handle) ", t = " (:t handle) ", last-change-t = " lastChangeT "]")))

(defn resource? [x]
  (instance? Resource x))

(defn handle [resource]
  (.-handle ^Resource resource))

(defn pull [resource]
  (d/pull (.-db ^Resource resource) (handle resource)))

(defn- resource [db handle last-change-t]
  (->Resource db handle last-change-t (volatile! nil)))

(defn- non-patient-last-change-t
  "Returns a last change t that is newer than any Bloom filter usable with `db`,
  because Bloom filters contain only Patient ids and so can't be used for other
  resources."
  [db]
  (inc (d/t db)))

(defn mk-resource [db handle]
  (resource
   db handle
   (if (identical? :fhir/Patient (:fhir/type handle))
     (or (d/patient-compartment-last-change-t db (:id handle)) (:t handle))
     (non-patient-last-change-t db))))

(defn resource-mapper
  "Returns a transducer that maps resource handles to resources with a last
  change t newer than `db`, so that Bloom filters are never used for them.

  Use `patient-mapper` for Patients evaluated as subjects, because Bloom filter
  checks need the last change t of their patient compartment."
  [db]
  (let [last-change-t (non-patient-last-change-t db)]
    (map #(resource db % last-change-t))))

(defn patient-mapper
  "Returns a transducer that maps Patient resource handles to resources.

  Prefer this transducer over mapping `mk-resource`, because with a batch
  database, it opens only one iterator to look up the patient compartment last
  change t of all handles."
  [db]
  (d/patient-compartment-last-change-t-xf
   db (fn [handle t] (resource db handle (or t (:t handle))))))

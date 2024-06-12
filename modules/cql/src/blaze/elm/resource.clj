(ns blaze.elm.resource
  (:require
   [blaze.db.api :as d]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.spec]
   [blaze.fhir.spec.type.protocols :as p])
  (:import
   [clojure.lang ILookup]))

(set! *warn-on-reflection* true)

;; A resource that is a wrapper of a resource-handle that will lazily pull the
;; resource content if some property other than :id is accessed.
(deftype Resource [db id handle ^long lastChangeT content]
  p/FhirType
  (-type [_]
    (p/-type handle))

  ILookup
  (valAt [r key]
    (.valAt r key nil))
  (valAt [_ key not-found]
    (case key
      :id id
      (-> (or @content (vreset! content @(d/pull-content db handle)))
          (get key not-found))))

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
  (-eval [expr _ _ _]
    expr)
  (-form [_]
    (list 'resource (name (p/-type handle)) id (rh/t handle)))

  Object
  (toString [_]
    (str (name (p/-type handle)) "[id = " id ", t = " (rh/t handle) ", last-change-t = " lastChangeT "]")))

(defn resource? [x]
  (instance? Resource x))

(defn- patient-last-change-t [db handle]
  (or (d/patient-compartment-last-change-t db (rh/id handle)) (rh/t handle)))

(defn- last-change-t [db handle]
  (if (identical? :fhir/Patient (p/-type handle))
    (patient-last-change-t db handle)
    (d/t db)))

(defn mk-resource [db handle]
  (Resource. db (rh/id handle) handle (last-change-t db handle) (volatile! nil)))

(defn resource-mapper [db]
  (map (partial mk-resource db)))

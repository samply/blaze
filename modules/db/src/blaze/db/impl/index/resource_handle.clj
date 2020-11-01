(ns blaze.db.impl.index.resource-handle
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.fhir.spec.type :as type])
  (:import
    [com.google.common.hash HashCode])
  (:refer-clojure :exclude [hash]))


(set! *warn-on-reflection* true)


(defrecord ResourceHandle [^int tid ^String id ^long t
                           ^HashCode hash ^long state]
  type/FhirType
  (-type [_]
    ;; TODO: maybe cache this
    (keyword "fhir" (codec/tid->type tid))))


(defn resource-handle
  "Creates a new resource handle.

  The type of that handle will be the keyword `:fhir/<resource-type>`."
  [tid id t hash state]
  (ResourceHandle. tid id t hash state))


(defn tid
  "Returns the tid of the resource handle."
  [resource-handle]
  (.tid ^ResourceHandle resource-handle))


(defn id
  "Returns the id of the resource handle."
  [resource-handle]
  (.id ^ResourceHandle resource-handle))


(defn t
  "Returns the t of the resource handle."
  [resource-handle]
  (.t ^ResourceHandle resource-handle))


(defn num-changes
  "Returns the number of changes of the resource of the handle."
  [resource-handle]
  (codec/state->num-changes (.state ^ResourceHandle resource-handle)))


(defn hash
  "Returns the hash of the resource handle."
  [resource-handle]
  (.hash ^ResourceHandle resource-handle))


(defn state
  "Returns the state of the resource handle."
  [resource-handle]
  (.state ^ResourceHandle resource-handle))


(defn resource-handle? [x]
  (instance? ResourceHandle x))


(defn deleted? [resource-handle]
  (codec/deleted? (.state ^ResourceHandle resource-handle)))

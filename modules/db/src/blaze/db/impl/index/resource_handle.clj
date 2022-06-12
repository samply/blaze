(ns blaze.db.impl.index.resource-handle
  (:refer-clojure :exclude [hash])
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec.type.protocols :as p])
  (:import
    [clojure.lang ILookup Numbers]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(deftype ResourceHandle [^int tid ^long did ^long t hash ^long num-changes op id]
  p/FhirType
  (-type [_]
    ;; TODO: maybe cache this
    (keyword "fhir" (codec/tid->type tid)))

  ILookup
  (valAt [resource-handle key]
    (.valAt resource-handle key nil))
  (valAt [_ key not-found]
    (case key
      :tid tid
      :did did
      :t t
      :hash hash
      :num-changes num-changes
      :op op
      :id id
      not-found))

  Object
  (toString [_]
    (format "%s/%s" (codec/tid->type tid) id))
  (equals [resource-handle x]
    (or (identical? resource-handle x)
        (and (instance? ResourceHandle x)
             (= tid (.-tid ^ResourceHandle x))
             (= did (.-did ^ResourceHandle x))
             (= t (.-t ^ResourceHandle x)))))
  (hashCode [_]
    (-> (Long/hashCode tid)
        (unchecked-multiply-int 31)
        (unchecked-add-int (Long/hashCode did))
        (unchecked-multiply-int 31)
        (unchecked-add-int (Long/hashCode t)))))


(defn state->num-changes
  "A resource is new if num-changes is 1."
  {:inline (fn [state] `(bit-shift-right (unchecked-long ~state) 8))}
  [state]
  (bit-shift-right (unchecked-long state) 8))


(defn state->op [^long state]
  ;; TODO: revise this unidiomatic style taken for performance reasons
  (if (Numbers/testBit state 1)
    :create
    (if (Numbers/testBit state 0)
      :delete
      :put)))


(defn resource-handle
  "Creates a new resource handle.

  The type of that handle will be the keyword `:fhir/<resource-type>`."
  [tid did t value-buffer]
  (let [hash (hash/from-byte-buffer! value-buffer)
        state (bb/get-long! value-buffer)]
    (ResourceHandle. tid did t hash (state->num-changes state) (state->op state)
                     (codec/id-from-byte-buffer value-buffer))))


(defn resource-handle? [x]
  (instance? ResourceHandle x))


(defn deleted?
  {:inline
   (fn [resource-handle]
     `(identical? :delete (.-op ~(with-meta resource-handle {:tag `ResourceHandle}))))}
  [resource-handle]
  (identical? :delete (.-op ^ResourceHandle resource-handle)))


(defn tid
  {:inline
   (fn [resource-handle]
     `(.-tid ~(with-meta resource-handle {:tag `ResourceHandle})))}
  [resource-handle]
  (.-tid ^ResourceHandle resource-handle))


(defn did
  "Returns the internal resource identifier of `resource-handle`."
  {:inline
   (fn [resource-handle]
     `(.-did ~(with-meta resource-handle {:tag `ResourceHandle})))}
  [resource-handle]
  (.-did ^ResourceHandle resource-handle))


(defn t
  "Returns the point in time `t` at which `resource-handle` was created."
  {:inline
   (fn [resource-handle]
     `(.-t ~(with-meta resource-handle {:tag `ResourceHandle})))}
  [resource-handle]
  (.-t ^ResourceHandle resource-handle))


(defn hash
  {:inline
   (fn [resource-handle]
     `(.-hash ~(with-meta resource-handle {:tag `ResourceHandle})))}
  [resource-handle]
  (.-hash ^ResourceHandle resource-handle))


(defn id
  "Returns the FHIR resource identifier of `resource-handle`."
  {:inline
   (fn [resource-handle]
     `(.-id ~(with-meta resource-handle {:tag `ResourceHandle})))}
  [resource-handle]
  (.-id ^ResourceHandle resource-handle))


(defn reference [resource-handle]
  (str (codec/tid->type (tid resource-handle)) "/" (id resource-handle)))

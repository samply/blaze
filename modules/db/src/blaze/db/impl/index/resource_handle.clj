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
  (valAt [rh key]
    (.valAt rh key nil))
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
  (equals [rh x]
    (or (identical? rh x)
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
   (fn [rh]
     `(identical? :delete (.-op ~(with-meta rh {:tag `ResourceHandle}))))}
  [rh]
  (identical? :delete (.-op ^ResourceHandle rh)))


(defn tid
  {:inline (fn [rh] `(.-tid ~(with-meta rh {:tag `ResourceHandle})))}
  [rh]
  (.-tid ^ResourceHandle rh))


(defn did
  {:inline (fn [rh] `(.-did ~(with-meta rh {:tag `ResourceHandle})))}
  [rh]
  (.-did ^ResourceHandle rh))


(defn t
  {:inline (fn [rh] `(.-t ~(with-meta rh {:tag `ResourceHandle})))}
  [rh]
  (.-t ^ResourceHandle rh))


(defn hash
  {:inline (fn [rh] `(.-hash ~(with-meta rh {:tag `ResourceHandle})))}
  [rh]
  (.-hash ^ResourceHandle rh))


(defn id
  {:inline (fn [rh] `(.-id ~(with-meta rh {:tag `ResourceHandle})))}
  [rh]
  (.-id ^ResourceHandle rh))


(defn reference [rh]
  (str (codec/tid->type (tid rh)) "/" (id rh)))

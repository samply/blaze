(ns blaze.db.impl.index.resource-handle
  "A resource handle is a detailed pointer to a resource version, containing not
  only the resource ID and hash, but also the logical timestamp `t` of that
  version.

  The implementation of the resource handle is done in Java. It's created via
  the `resource-handle!` function. The access of it's data is performed via
  keyword lookup. The relevant properties are:

  * :fhir/type - the FHIR type like :fhir/Patient
  * :tid - the internal type ID as integer
  * :id - the logical FHIR ID
  * :t - logical timestamp `t` of the transaction the version of the resource
         was created
  * :hash - the content hash of the resource version
  * :num-changes - the number of changes happened to the resource up to `t`
  * :op - the transaction operator that lead to this version. One of :create,
          :put or :delete"
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]
   [blaze.fhir.hash :as hash])
  (:import
   [blaze.db.impl.index ResourceHandle]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- no-purged-at? [vb]
  (< (bb/remaining vb) 8))

(defn- not-purged?! [base-t vb]
  (< (long base-t) (bb/get-long! vb)))

(defn resource-handle!
  "Creates a new resource handle when not purged at `base-t`.

  The :fhir/type of that handle will be the keyword `:fhir/<resource-type>`
  taken from `tid`."
  [tid id t base-t vb]
  (let [hash (hash/from-byte-buffer! vb)
        state (bb/get-long! vb)]
    (when (or (no-purged-at? vb) (not-purged?! base-t vb))
      (ResourceHandle.
       (keyword "fhir" (codec/tid->type tid))
       tid
       id
       t
       hash
       state))))

(defn resource-handle?
  "Returns `true` if `x` is a resource handle."
  [x]
  (instance? ResourceHandle x))

(defn deleted?
  "Returns `true` if `resource-handle` is deleted."
  [resource-handle]
  (identical? :delete (:op resource-handle)))

(defn tid-id [resource-handle]
  (codec/tid-id (:tid resource-handle) (codec/id-byte-string (:id resource-handle))))

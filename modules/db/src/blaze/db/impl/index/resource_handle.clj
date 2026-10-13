(ns blaze.db.impl.index.resource-handle
  "A resource handle is a detailed pointer to a resource version, containing not
  only the resource ID and hash, but also the logical timestamp `t` of that
  version.

  The implementation of the resource handle is done in Java. It's created via
  the `resource-handle!` function. The access of its data is performed via
  keyword lookup. The relevant properties are:

  * :fhir/type - the FHIR type like :fhir/Patient determined by :tid
  * :tid - the internal type ID as integer
  * :id - the logical FHIR ID
  * :t - logical timestamp `t` of the transaction the version of the resource
         was created
  * :hash - the content hash of the resource version. The hash is stored inline
            and a new hash instance is created on each lookup. Use
            :hash-prefix or `hash-into-byte-string-builder!` if no hash
            instance is needed.
  * :hash-prefix - the first 4 bytes of the hash as unsigned integer
  * :num-changes - the number of changes happened to the resource up to `t`
  * :op - the transaction operator that lead to this version. One of :create,
          :put or :delete"
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec])
  (:import
   [blaze ByteString$Builder]
   [blaze.db.impl.index ResourceHandle]
   [java.nio ByteBuffer]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn- no-purged-at? [vb]
  (< (bb/remaining vb) 8))

(defn- not-purged?! [base-t vb]
  (< (long base-t) (bb/get-long! vb)))

(defn resource-handle!
  "Creates a new resource handle when not purged at `base-t`.

  The :fhir/type of that handle will be the keyword `:fhir/<resource-type>`
  determined by `tid` on lookup. That lookup throws an IllegalStateException if
  `tid` is unknown."
  [tid id t base-t vb]
  (let [h0 (bb/get-long! vb)
        h1 (bb/get-long! vb)
        h2 (bb/get-long! vb)
        h3 (bb/get-long! vb)
        state (bb/get-long! vb)]
    (when (or (no-purged-at? vb) (not-purged?! base-t vb))
      (ResourceHandle.
       tid
       id
       t
       h0 h1 h2 h3
       state))))

(defn resource-handle?
  "Returns `true` if `x` is a resource handle."
  [x]
  (instance? ResourceHandle x))

(defn hash-into-byte-string-builder!
  "Puts the hash of `resource-handle` into `builder`.

  Doesn't create a hash instance like `(:hash resource-handle)` does."
  [builder resource-handle]
  (.copyHashTo ^ResourceHandle resource-handle ^ByteString$Builder builder)
  builder)

(defn tid-into-byte-buffer!
  "Puts the tid of `resource-handle` into `byte-buffer`.

  Doesn't box the tid like `(:tid resource-handle)` does."
  [byte-buffer resource-handle]
  (.copyTidTo ^ResourceHandle resource-handle ^ByteBuffer byte-buffer)
  byte-buffer)

(defn id-into-byte-buffer!
  "Puts the id of `resource-handle` into `byte-buffer`.

  Doesn't create a byte string like `(codec/id-byte-string (:id
  resource-handle))` does."
  [byte-buffer resource-handle]
  (.copyIdTo ^ResourceHandle resource-handle ^ByteBuffer byte-buffer)
  byte-buffer)

(defn hash-prefix-into-byte-buffer!
  "Puts the hash prefix of `resource-handle` into `byte-buffer`.

  Doesn't box the hash prefix like `(:hash-prefix resource-handle)` does."
  [byte-buffer resource-handle]
  (.copyHashPrefixTo ^ResourceHandle resource-handle ^ByteBuffer byte-buffer)
  byte-buffer)

(defn with-id
  "Returns a copy of `resource-handle` with `id`, which has to be equal to the
  id of `resource-handle`.

  Used to share the id instance with another object."
  [resource-handle id]
  (.withId ^ResourceHandle resource-handle ^String id))

(defn deleted?
  "Returns `true` if `resource-handle` is deleted."
  [resource-handle]
  (identical? :delete (:op resource-handle)))

(defn tid-id [resource-handle]
  (codec/tid-id (:tid resource-handle) (codec/id-byte-string (:id resource-handle))))

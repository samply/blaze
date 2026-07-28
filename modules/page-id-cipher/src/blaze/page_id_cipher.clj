(ns blaze.page-id-cipher
  "Cipher used to encrypt and decrypt page IDs.

  Paging links Blaze hands out don't carry the query params of the page in the
  clear. Instead the query params are encrypted into an opaque page ID that is
  used as a path segment of the paging URL. This component provides the AEAD
  primitive used for that. The encryption and decryption themselves happen in
  `blaze.middleware.fhir.decrypt-page-id`.

  Encrypting page IDs has three effects. First, clients can't tamper with the
  query params of a page, because a page ID that wasn't produced by Blaze itself
  won't decrypt. Second, internals like the point in time `t` of the database
  value the page is based on, or the offset into the result set, stay opaque.
  Third, page IDs expire, because the keys they were encrypted with are rotated
  out of the key set; see the section on key rotation below.

  ## Key Set Storage

  The cipher is backed by a Tink key set. That key set has to be shared by all
  Blaze instances in a deployment, because a paging link created by one instance
  has to be resolvable by every other instance, and it has to survive restarts.
  Rather than introducing a store of its own, the cipher keeps the key set in
  the admin database (`:blaze.db.admin/node`) as a `DocumentReference` resource
  with the following properties:

  * `identifier` - a single identifier with the value `page-id-cipher` under
    which the resource is looked up,
  * `status` - `current`, because FHIR requires the property,
  * `content[0].attachment.data` - the serialized key set, Base64 encoded.

  A `DocumentReference` is used because it can carry an arbitrary binary
  payload together with metadata. The key set goes into the base64Binary `data`
  property of its first attachment and the well-known `identifier` value makes
  the resource findable with the `identifier` search param, without having to
  remember its logical ID anywhere.

  The admin database is used because the key set is operational state of the
  server, like the `Task` resources of the job scheduler. It doesn't belong in
  the main database that is served by the FHIR API.

  The key set itself is stored unencrypted, but the `DocumentReference` resource
  holding it isn't accessible via any API. The FHIR API only ever reads from the
  main database, and of the admin database, only `Task` resources are exposed.

  ## Lifecycle

  On init, the key set resource is looked up by its identifier and created with
  a freshly generated key set if it doesn't exist yet. That create is
  conditional on the very same identifier, so multiple Blaze instances starting
  concurrently end up with exactly one key set resource. Transactions are
  serialized through the transaction log, and the conditional creates of the
  instances that lose the race are no-ops.

  Each instance subscribes to the changed `DocumentReference` resources of the
  admin node and refreshes its in-memory key set as soon as the key set resource
  changes. That way a rotation performed by one instance reaches all other
  instances.

  ## Key Rotation

  Paging links have to expire, and rotating the keys is what makes them expire.

  A page ID carries the point in time `t` of the database value the paging
  started at, and every following page is read from that same database value. So
  a paging link keeps a fixed, historic database state reachable, including the
  resources that were deleted after `t`. Clients must not be able to use such a
  link indefinitely, because deleted resources have to become inaccessible
  eventually, for GDPR reasons. Once the keys a page ID was encrypted with are
  gone from the key set, the page ID no longer decrypts and the database state it
  pointed to can't be reached through it anymore.

  The keys are therefore rotated every `key-rotation-period`, one hour by
  default, by writing an updated key set back into the key set resource. That
  write uses the version ID of the resource read as `:if-match` precondition, so
  two instances rotating at the same time can't lose each other's keys.

  Rotation retires keys gradually (see `blaze.page-id-cipher.impl`): a key is
  kept for three more rotation periods after it stopped being primary. So with
  the default rotation period, a paging link stays usable for at least three
  hours and is guaranteed to be dead after five."
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.module :as m]
   [blaze.page-id-cipher.impl :as impl]
   [blaze.page-id-cipher.spec]
   [blaze.scheduler :as sched]
   [blaze.scheduler.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [com.google.crypto.tink Aead InsecureSecretKeyAccess TinkProtoKeysetFormat]
   [java.util Base64]
   [java.util.concurrent Flow$Subscriber]))

(set! *warn-on-reflection* true)

(def ^:private ^:const key-set-identifier
  "The identifier value the key set `DocumentReference` is looked up with."
  "page-id-cipher")

(defn- find-key-set-resource
  "Returns a CompletableFuture that will complete with the resource handle of
  the key set `DocumentReference` in `db`, or `nil` if it doesn't exist yet."
  [db]
  (log/trace "try to find the key set resource")
  (-> (d/type-query db "DocumentReference" [["identifier" key-set-identifier]])
      (ac/then-apply coll/first)))

(defn- b64-encode [bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn- encode-key-set-handle [key-set-handle]
  (-> key-set-handle
      (TinkProtoKeysetFormat/serializeKeyset (InsecureSecretKeyAccess/get))
      (b64-encode)))

(defn- key-set-attachment [key-set-handle]
  (type/attachment {:data (type/base64Binary (encode-key-set-handle key-set-handle))}))

(defn- key-set-content [key-set-handle]
  {:fhir/type :fhir.DocumentReference/content
   :attachment (key-set-attachment key-set-handle)})

(defn- key-set-resource
  "Returns a new key set `DocumentReference` resource with a freshly generated
  key set as first attachment."
  [context]
  {:fhir/type :fhir/DocumentReference
   :id (m/luid context)
   :identifier [(type/identifier {:value (type/string key-set-identifier)})]
   :status #fhir/code "current"
   :content
   [(key-set-content (impl/gen-new-key-set-handle))]})

(defn- key-set-resource-create-op
  "Returns a transaction operator creating a new key set resource, conditional
  on the absence of a resource with the key set identifier.

  Because transactions are serialized through the transaction log, at most one
  of the creates submitted by concurrently starting Blaze instances will take
  effect. The others become no-ops."
  [context]
  [:create (key-set-resource context) [["identifier" key-set-identifier]]])

(defn- find-or-create-key-set-resource
  "Returns a CompletableFuture that will complete with the `DocumentReference`
  resource with the key set as first attachment, creating that resource with a
  freshly generated key set if it doesn't exist yet."
  {:arglists '([context])}
  ([{:keys [node] :as context}]
   (find-or-create-key-set-resource context (d/db node)))
  ([{:keys [node] :as context} db]
   (-> (find-key-set-resource db)
       (ac/then-compose
        (fn [handle]
          (if handle
            (d/pull node handle)
            (-> (d/transact node [(key-set-resource-create-op context)])
                (ac/then-compose (partial find-or-create-key-set-resource context)))))))))

(defn- b64-decode [s]
  (.decode (Base64/getDecoder) ^String s))

(defn- parse-key-set [data]
  (-> (b64-decode data)
      (TinkProtoKeysetFormat/parseKeyset (InsecureSecretKeyAccess/get))))

(defn- decode-key-set-handle
  "Returns the key set handle decoded from the first attachment of
  `key-set-resource`."
  {:arglists '([key-set-resource])}
  [{[{{:keys [data]} :attachment}] :content}]
  (parse-key-set (:value data)))

(defn- decode-state
  "Returns the cipher state decoded from `key-set-resource`: the key set handle
  together with the AEAD primitive derived from it."
  [key-set-resource]
  (let [key-set-handle (decode-key-set-handle key-set-resource)]
    {:key-set-handle key-set-handle
     :aead (impl/get-aead key-set-handle)}))

;; Subscriber to the changed `DocumentReference` resources of the admin node.
;; Keeps `state` in sync with the key set resource, which is how rotations
;; performed by one Blaze instance reach all other instances.
(deftype DocumentReferenceSubscriber [node state ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! subscription 1))
  (onNext [_ document-reference-handles]
    (log/trace "Got" (count document-reference-handles)
               "changed document-reference(s)")
    (run!
     (fn [{[identifier] :identifier :as document-reference}]
       (when (= key-set-identifier (:value (:value identifier)))
         (log/debug "Refresh key set")
         (reset! state (decode-state document-reference))))
     @(d/pull-many node document-reference-handles))
    (flow/request! subscription 1))
  (onError [_ e]
    (log/fatal "Page ID cipher failed. Please restart Blaze. Cause:" (ex-message e))
    (flow/cancel! subscription))
  (onComplete [_]))

;; The component itself. It's an AEAD primitive that delegates to the primitive
;; of the current `state`, so that all users of the component pick up key
;; rotations without having to be notified. The `future` is the handle of the
;; scheduled key rotation, used to cancel it on shutdown.
(defrecord Cipher [state future]
  Aead
  (encrypt [_ plaintext associatedData]
    (.encrypt ^Aead (:aead @state) plaintext associatedData))
  (decrypt [_ ciphertext associatedData]
    (.decrypt ^Aead (:aead @state) ciphertext associatedData)))

(defn- update-key-set-handle
  "Returns `key-set-resource` with its key set handle replaced by the result of
  applying `f` to it."
  [key-set-resource f]
  (let [handle (decode-key-set-handle key-set-resource)]
    (assoc key-set-resource :content [(key-set-content (f handle))])))

(defn- update-tx-op
  "Returns a transaction operator updating `resource`, conditional on the
  version ID it was read at.

  The precondition ensures that a Blaze instance never overwrites a key set
  another instance has rotated in the meantime."
  [{{version-id :versionId} :meta :as resource}]
  [:put resource [:if-match (parse-long (:value version-id))]])

(defn- update-resource [node resource f]
  (d/transact node [(update-tx-op (f resource))]))

(defn- rotate-keys* [node key-set-resource]
  (log/debug "Rotate page ID cipher keys")
  (update-resource node key-set-resource #(update-key-set-handle % impl/rotate-keys)))

(defn- rotate-keys
  "Rotates the keys in the key set of the `DocumentReference` resource with the
  identifier `page-id-cipher` in `node`.

  Writing the rotated key set back into the resource will notify all Blaze
  instances subscribed to the changed `DocumentReference` resources of `node`,
  including this instance."
  ([node]
   (when-let [handle @(find-key-set-resource (d/db node))]
     @(rotate-keys* node @(d/pull node handle)))))

(defn- schedule-key-rotation
  "Schedules the key rotation to run every `key-rotation-period`, one hour by
  default.

  Returns a future that can be used to cancel the rotation."
  [{:keys [node scheduler key-rotation-period]
    :or {key-rotation-period (time/hours 1)}}]
  (sched/schedule-at-fixed-rate scheduler #(rotate-keys node)
                                key-rotation-period key-rotation-period))

(defmethod m/pre-init-spec :blaze/page-id-cipher [_]
  (s/keys :req-un [:blaze.db/node :blaze/scheduler :blaze/clock :blaze/rng-fn]
          :opt-un [:blaze.page-id-cipher/key-rotation-period]))

(defmethod ig/init-key :blaze/page-id-cipher
  [_ {:keys [node] :as context}]
  (log/info "Init page ID cipher")
  (let [state (atom (decode-state @(find-or-create-key-set-resource context)))
        publisher (d/changed-resources-publisher node "DocumentReference")
        subscriber (->DocumentReferenceSubscriber node state nil)]
    (flow/subscribe! publisher subscriber)
    (->Cipher state (schedule-key-rotation context))))

(defmethod ig/halt-key! :blaze/page-id-cipher
  [_ {:keys [future]}]
  (log/info "Stop page ID cipher")
  (sched/cancel future false))

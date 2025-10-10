(ns blaze.page-id-cipher
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

(def ^:private ^:const identifier
  "page-id-cipher")

(defn- find-key-set-resource [db]
  (log/trace "try to find the key set resource")
  (coll/first (d/type-query db "DocumentReference" [["identifier" identifier]])))

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

(defn- key-set-resource [context]
  {:fhir/type :fhir/DocumentReference
   :id (m/luid context)
   :identifier [(type/identifier {:value (type/string identifier)})]
   :status #fhir/code "current"
   :content
   [(key-set-content (impl/gen-new-key-set-handle))]})

(defn- key-set-resource-create-op [context]
  [:create (key-set-resource context) [["identifier" identifier]]])

(defn- find-or-create-key-set-resource
  "Returns a CompletableFuture that will complete with the DocumentReference
  resource with the key set as first attachment."
  {:arglists '([context])}
  ([{:keys [node] :as context}]
   (find-or-create-key-set-resource context (d/db node)))
  ([{:keys [node] :as context} db]
   (if-let [handle (find-key-set-resource db)]
     (d/pull node handle)
     (-> (d/transact node [(key-set-resource-create-op context)])
         (ac/then-compose (partial find-or-create-key-set-resource context))))))

(defn- b64-decode [s]
  (.decode (Base64/getDecoder) ^String s))

(defn- parse-key-set [data]
  (-> (b64-decode data)
      (TinkProtoKeysetFormat/parseKeyset (InsecureSecretKeyAccess/get))))

(defn- decode-key-set-handle
  {:argLists '([key-set-resource])}
  [{[{{:keys [data]} :attachment}] :content}]
  (parse-key-set (:value data)))

(defn- decode-state [key-set-resource]
  (let [key-set-handle (decode-key-set-handle key-set-resource)]
    {:key-set-handle key-set-handle
     :aead (impl/get-aead key-set-handle)}))

(deftype TaskSubscriber [node state ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! subscription 1))
  (onNext [_ document-reference-handles]
    (log/trace "Got" (count document-reference-handles)
               "changed document-reference(s)")
    (run!
     (fn [{[{{value :value} :value}] :identifier :as document-reference}]
       (when (= identifier value)
         (log/debug "Refresh key set")
         (reset! state (decode-state document-reference))))
     @(d/pull-many node document-reference-handles))
    (flow/request! subscription 1))
  (onError [_ e]
    (log/fatal "Page ID cipher failed. Please restart Blaze. Cause:" (ex-message e))
    (flow/cancel! subscription))
  (onComplete [_]))

(defrecord Cipher [state future]
  Aead
  (encrypt [_ plaintext associatedData]
    (.encrypt ^Aead (:aead @state) plaintext associatedData))
  (decrypt [_ ciphertext associatedData]
    (.decrypt ^Aead (:aead @state) ciphertext associatedData)))

(defn- update-key-set-handle [key-set-resource f]
  (let [handle (decode-key-set-handle key-set-resource)]
    (assoc key-set-resource :content [(key-set-content (f handle))])))

(defn- update-tx-op [{{version-id :versionId} :meta :as resource}]
  [:put resource [:if-match (parse-long (:value version-id))]])

(defn- update-resource [node resource f]
  (d/transact node [(update-tx-op (f resource))]))

(defn- rotate-keys
  "Rotates the keys in the key set available at DocumentReference with
  identifier `page-id-cipher` in `node`."
  ([node]
   (when-let [handle (find-key-set-resource (d/db node))]
     (rotate-keys node @(d/pull node handle))))
  ([node key-set-resource]
   (log/debug "Rotate page ID cipher keys")
   (update-resource node key-set-resource #(update-key-set-handle % impl/rotate-keys))))

(defn- schedule-key-rotation
  [{:keys [node scheduler key-rotation-period]
    :or {key-rotation-period (time/hours 1)}}]
  (sched/schedule-at-fixed-rate scheduler (partial rotate-keys node)
                                key-rotation-period key-rotation-period))

(defmethod m/pre-init-spec :blaze/page-id-cipher [_]
  (s/keys :req-un [:blaze.db/node :blaze/scheduler :blaze/clock :blaze/rng-fn]
          :opt-un [:blaze.page-id-cipher/key-rotation-period]))

(defmethod ig/init-key :blaze/page-id-cipher
  [_ {:keys [node] :as context}]
  (log/info "Init page ID cipher")
  (let [state (atom (decode-state @(find-or-create-key-set-resource context)))
        publisher (d/changed-resources-publisher node "DocumentReference")
        subscriber (->TaskSubscriber node state nil)]
    (flow/subscribe! publisher subscriber)
    (->Cipher state (schedule-key-rotation context))))

(defmethod ig/halt-key! :blaze/page-id-cipher
  [_ {:keys [future]}]
  (log/info "Stop page ID cipher")
  (sched/cancel future false))

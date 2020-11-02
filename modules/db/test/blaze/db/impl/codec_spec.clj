(ns blaze.db.impl.codec-spec
  (:require
    [blaze.db.api-spec]
    [blaze.db.bytes :as bytes]
    [blaze.db.bytes-spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.codec.spec]
    [blaze.fhir.spec]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.test.check]
    [clojure.test.check.generators :as gen2]
    [cognitect.anomalies :as anom])
  (:import
    [java.time ZoneId]))



;; ---- Internal Identifiers --------------------------------------------------

(s/def :blaze.db/tid
  (s/with-gen int? gen/int))


(s/def :blaze.db/spid
  (s/with-gen int? gen/int))


(s/def :blaze.db/c-hash
  (s/with-gen int? gen/int))


(s/def :blaze.db/v-hash
  (s/with-gen int? gen/int))


(s/def :blaze.db/unit-hash
  (s/with-gen int? gen/int))


(defn byte-array-gen [max-size]
  #(gen/fmap byte-array (gen/vector gen2/byte 1 max-size)))


(s/def :blaze.db/id-bytes
  (s/with-gen bytes? (byte-array-gen 64)))



;; ---- Byte Array Functions --------------------------------------------------

(s/fdef codec/id-bytes
  :args (s/cat :id string?)
  :ret :blaze.db/id-bytes)



;; ---- Key Functions ---------------------------------------------------------

(s/fdef codec/t-key
  :args (s/cat :t :blaze.db/t)
  :ret bytes?)



;; ---- SearchParamValue Index ------------------------------------------------

(s/def :blaze.db/sp-value-resource-key
  bytes?)


(s/fdef codec/sp-value-resource-key
  :args (s/cat :c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value bytes?
               :id (s/? :blaze.db/id-bytes)
               :hash (s/? :blaze.resource/hash))
  :ret :blaze.db/sp-value-resource-key)



;; ---- ResourceValue Index ---------------------------------------------------

(s/def :blaze/resource-value-key
  bytes?)


(s/def :blaze.db/hash-prefix
  (s/and bytes? #(= codec/hash-prefix-size (alength %))))


(s/fdef codec/resource-sp-value-key
  :args (s/cat :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :hash (s/alt :hash :blaze.resource/hash :hash-prefix :blaze.db/hash-prefix)
               :c-hash :blaze.db/c-hash
               :value (s/? bytes?))
  :ret :blaze/resource-value-key)



;; ---- CompartmentSearchParamValue Index -------------------------------------


(s/def :blaze.db/compartment-search-param-value-key
  bytes?)


(s/fdef codec/compartment-search-param-value-key
  :args (s/cat :co-c-hash :blaze.db/c-hash
               :co-res-id :blaze.db/id-bytes
               :sp-c-hash :blaze.db/c-hash
               :tid :blaze.db/tid
               :value bytes?
               :id (s/? :blaze.db/id-bytes)
               :hash (s/? :blaze.resource/hash))
  :ret :blaze.db/compartment-search-param-value-key)



;; ---- CompartmentResourceValue Index ----------------------------------------

(s/def :blaze.db/compartment-resource-value-key
  bytes?)


(s/fdef codec/compartment-resource-value-key
  :args (s/cat :co-c-hash :blaze.db/c-hash
               :co-res-id :blaze.db/id-bytes
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :hash (s/alt :hash :blaze.resource/hash :hash-prefix :blaze.db/hash-prefix)
               :sp-c-hash :blaze.db/c-hash
               :value (s/? bytes?))
  :ret :blaze.db/compartment-resource-value-key)



;; ---- ResourceType Index ----------------------------------------------------

(s/def :blaze/resource-type-key
  bytes?)


(s/fdef codec/resource-type-key
  :args (s/cat :tid :blaze.db/tid :id (s/? :blaze.db/id-bytes))
  :ret :blaze/resource-type-key
  :fn #(let [tid (codec/resource-type-key->tid (:ret %))
             id (when (-> % :args :id) (codec/resource-type-key->id (:ret %)))]
         (and (= tid (-> % :args :tid))
              (or (and (nil? id) (nil? (-> % :args :id)))
                  (bytes/= id (-> % :args :id))))))


(s/fdef codec/resource-type-key->tid
  :args (s/cat :k :blaze/resource-type-key)
  :ret :blaze.db/tid)


(s/fdef codec/resource-type-key->id
  :args (s/cat :k :blaze/resource-type-key)
  :ret :blaze.db/id-bytes)



;; ---- CompartmentResourceType Index -----------------------------------------

(s/def :blaze.db/compartment-resource-type-key
  bytes?)


(s/fdef codec/compartment-resource-type-key
  :args (s/cat :co-c-hash :blaze.db/c-hash
               :co-res-id :blaze.db/id-bytes
               :tid :blaze.db/tid
               :id (s/? :blaze.db/id-bytes))
  :ret :blaze.db/compartment-resource-type-key
  :fn #(let [co-c-hash (codec/compartment-resource-type-key->co-c-hash (:ret %))
             co-res-id (codec/compartment-resource-type-key->co-res-id (:ret %))
             tid (codec/compartment-resource-type-key->tid (:ret %))
             id (when (-> % :args :id) (codec/compartment-resource-type-key->id (:ret %)))]
         (and (= co-c-hash (-> % :args :co-c-hash))
              (bytes/= co-res-id (-> % :args :co-res-id))
              (= tid (-> % :args :tid))
              (or (and (nil? id) (nil? (-> % :args :id)))
                  (bytes/= id (-> % :args :id))))))


(s/fdef codec/compartment-resource-type-key->co-c-hash
  :args (s/cat :k :blaze.db/compartment-resource-type-key)
  :ret :blaze.db/c-hash)


(s/fdef codec/compartment-resource-type-key->co-res-id
  :args (s/cat :k :blaze.db/compartment-resource-type-key)
  :ret :blaze.db/id-bytes)


(s/fdef codec/compartment-resource-type-key->tid
  :args (s/cat :k :blaze.db/compartment-resource-type-key)
  :ret :blaze.db/tid)


(s/fdef codec/compartment-resource-type-key->id
  :args (s/cat :k :blaze.db/compartment-resource-type-key)
  :ret :blaze.db/id-bytes)



;; ---- ResourceAsOf Index ----------------------------------------------------

(s/def :blaze/resource-as-of-key
  bytes?)


(defn resource-as-of-key-check
  [{{:keys [tid] {:keys [id t] :as more} :more} :args :keys [ret]}]
  (let [tid' (codec/resource-as-of-key->tid ret)
        id' (when more (codec/resource-as-of-key->id ret))
        t' (when more (codec/resource-as-of-key->t ret))]
    (if more
      (and (= tid' tid) (bytes/= id' id) (= t' t))
      (= tid' tid))))


(s/fdef codec/resource-as-of-key
  :args (s/cat :tid :blaze.db/tid :more (s/? (s/cat :id :blaze.db/id-bytes :t :blaze.db/t)))
  :ret :blaze/resource-as-of-key
  :fn resource-as-of-key-check)


(s/fdef codec/resource-as-of-key->tid
  :args (s/cat :k :blaze/resource-as-of-key)
  :ret :blaze.db/tid)


(s/fdef codec/resource-as-of-key->id
  :args (s/cat :k :blaze/resource-as-of-key)
  :ret :blaze.db/id-bytes)


(s/fdef codec/state
  :args (s/cat :num-changes nat-int? :op :blaze.db/op)
  :ret :blaze.db/state)


(s/fdef codec/state->num-changes
  :args (s/cat :state :blaze.db/state)
  :ret nat-int?)


(s/fdef codec/state->op
  :args (s/cat :state :blaze.db/state)
  :ret :blaze.db/op)


(s/def :blaze/resource-as-of-value
  (s/and bytes? #(= (+ codec/hash-size codec/state-size) (alength %))))


(s/fdef codec/resource-as-of-value
  :args (s/cat :hash :blaze.resource/hash :state :blaze.db/state)
  :ret :blaze/resource-as-of-value
  :fn #(let [hash (codec/resource-as-of-value->hash (:ret %))
             state (codec/resource-as-of-value->state (:ret %))]
         (and (= hash (-> % :args :hash))
              (= state (-> % :args :state)))))


(s/fdef codec/resource-as-of-value->hash
  :args (s/cat :value :blaze/resource-as-of-value)
  :ret (s/nilable :blaze.resource/hash))


(s/fdef codec/resource-as-of-value->state
  :args (s/cat :value :blaze/resource-as-of-value)
  :ret :blaze.db/state)



;; ---- TypeAsOf Index ----------------------------------------------------

(s/def :blaze.db/type-as-of-key
  bytes?)


(s/fdef codec/type-as-of-key
  :args (s/cat :tid :blaze.db/tid :t :blaze.db/t :id (s/? :blaze.db/id-bytes))
  :ret :blaze.db/type-as-of-key
  :fn #(let [tid (codec/type-as-of-key->tid (:ret %))
             t (codec/type-as-of-key->t (:ret %))
             id (when (-> % :args :id) (codec/type-as-of-key->id (:ret %)))]
         (and (= tid (-> % :args :tid))
              (= t (-> % :args :t))
              (or (and (nil? id) (nil? (-> % :args :id)))
                  (bytes/= id (-> % :args :id))))))


(s/fdef codec/type-as-of-key->tid
  :args (s/cat :k :blaze.db/type-as-of-key)
  :ret :blaze.db/tid)


(s/fdef codec/type-as-of-key->t
  :args (s/cat :k :blaze.db/type-as-of-key)
  :ret :blaze.db/t)


(s/fdef codec/type-as-of-key->id
  :args (s/cat :k :blaze.db/type-as-of-key)
  :ret :blaze.db/id-bytes)



;; ---- SystemAsOf Index ----------------------------------------------------

(s/def :blaze.db/system-as-of-key
  bytes?)


(s/fdef codec/system-as-of-key
  :args (s/cat :t :blaze.db/t :tid (s/? :blaze.db/tid) :id (s/? :blaze.db/id-bytes))
  :ret :blaze.db/system-as-of-key)


(s/fdef codec/system-as-of-key->t
  :args (s/cat :k :blaze.db/system-as-of-key)
  :ret :blaze.db/t)


(s/fdef codec/system-as-of-key->tid
  :args (s/cat :k :blaze.db/system-as-of-key)
  :ret :blaze.db/tid)


(s/fdef codec/system-as-of-key->id
  :args (s/cat :k :blaze.db/system-as-of-key)
  :ret :blaze.db/id-bytes)



;; ---- Other Functions -------------------------------------------------------

(s/fdef codec/tid
  :args (s/cat :type :fhir.type/name)
  :ret :blaze.db/tid)


(s/fdef codec/tid->type
  :args (s/cat :tid :blaze.db/tid)
  :ret :fhir.type/name)


(s/fdef codec/c-hash
  :args (s/cat :code string?)
  :ret int?)


(s/fdef codec/v-hash
  :args (s/cat :value string?)
  :ret bytes?)


(s/fdef codec/tid-id
  :args (s/cat :type :blaze.db/tid :id :blaze.db/id-bytes)
  :ret bytes?)


(s/fdef codec/string
  :args (s/cat :string string?)
  :ret bytes?)


(s/fdef codec/date-lb
  :args (s/cat :zone-id #(instance? ZoneId %)
               :date-time (s/or :date system/date? :date-time system/date-time?))
  :ret bytes?)


(s/fdef codec/date-lb?
  :args (s/cat :b bytes? :offset nat-int?)
  :ret boolean?)


(s/fdef codec/date-ub?
  :args (s/cat :b bytes? :offset nat-int?)
  :ret boolean?)


(s/fdef codec/date-ub
  :args (s/cat :zone-id #(instance? ZoneId %)
               :date-time (s/or :date system/date? :date-time system/date-time?))
  :ret bytes?)


(s/fdef codec/date-lb-ub
  :args (s/cat :lb bytes? :ub bytes?)
  :ret bytes?)


(s/fdef codec/number
  :args (s/cat :number number?)
  :ret bytes?)


(s/fdef codec/quantity
  :args (s/cat :unit (s/nilable string?) :value number?)
  :ret bytes?)



;; ---- Transaction -----------------------------------------------------------

(s/fdef codec/tx-by-instant-key
  :args (s/cat :instant inst?)
  :ret bytes?)


(s/fdef codec/decode-tx
  :args (s/cat :bs bytes? :t :blaze.db/t)
  :ret :blaze.db/tx)


(s/fdef codec/tx-success-entries
  :args (s/cat :t :blaze.db/t :tx-instant inst?))


(s/fdef codec/tx-error-entries
  :args (s/cat :t :blaze.db/t :anomaly ::anom/anomaly))

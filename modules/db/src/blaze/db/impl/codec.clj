(ns blaze.db.impl.codec
  (:require
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.byte-string :as bs]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.util :as fhir-util]
    [cheshire.core :as cheshire]
    [cognitect.anomalies :as anom])
  (:import
    [blaze.fhir.spec.type.system DateTimeYear DateTimeYearMonth
                                 DateTimeYearMonthDay]
    [com.github.benmanes.caffeine.cache CacheLoader Caffeine]
    [com.google.common.hash HashCode Hashing]
    [com.google.common.io BaseEncoding]
    [com.google.protobuf ByteString]
    [java.nio ByteBuffer]
    [java.nio.charset Charset StandardCharsets]
    [java.time Instant LocalDate LocalDateTime OffsetDateTime Year YearMonth
               ZoneId ZoneOffset]
    [java.util Arrays]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)



;; ---- Sizes of Byte Arrays --------------------------------------------------

(def ^:const ^int hash-size 32)
(def ^:const ^int hash-prefix-size 4)
(def ^:const ^int c-hash-size Integer/BYTES)
(def ^:const ^int v-hash-size Integer/BYTES)
(def ^:const ^int tid-size Integer/BYTES)
(def ^:const ^int t-size Long/BYTES)
(def ^:const ^int state-size Long/BYTES)
(def ^:const ^int tx-time-size Long/BYTES)
(def ^:const ^int max-id-size 64)



;; ---- Instances -------------------------------------------------------------

(def ^Charset iso-8859-1 StandardCharsets/ISO_8859_1)

(def ^Charset utf-8 StandardCharsets/UTF_8)



;; ---- Type Identifier -------------------------------------------------------

(defn- memoize-1 [f]
  (let [mem
        (-> (Caffeine/newBuilder)
            (.build
              (reify CacheLoader
                (load [_ x]
                  (f x)))))]
    (fn [x]
      (.get mem x))))


(def tid
  "Internal type identifier.

  Returns an integer."
  (memoize-1
    (fn [type]
      (.asInt (.hashBytes (Hashing/murmur3_32) (.getBytes ^String type iso-8859-1))))))


(let [kvs (->> (fhir-util/resources)
               (map (fn [{:keys [type]}] [(tid type) type]))
               (sort-by first))
      tid->idx (int-array (map first kvs))
      idx->type (object-array (map second kvs))]
  (defn tid->type [tid]
    (let [idx (Arrays/binarySearch tid->idx ^long tid)]
      (when (nat-int? idx)
        (aget idx->type idx)))))



;; ---- Byte Array Functions --------------------------------------------------


(defn bytes-eq-without-t [^bytes a ^bytes b]
  (Arrays/equals a 0 (- (alength a) t-size)
                 b 0 (- (alength b) t-size)))


(defn hex [bs]
  (.encode (BaseEncoding/base16) bs))


(defn id-bytes [^String id]
  (.getBytes id iso-8859-1))


(defn id
  ([^bytes id-bytes]
   (String. id-bytes iso-8859-1))
  ([^bytes id-bytes ^long offset ^long length]
   (String. id-bytes offset length iso-8859-1)))



;; ---- Key Functions ---------------------------------------------------------

(defn descending-long
  "Converts positive longs so that they decrease from 0xFFFFFFFFFFFFFF.

  This function is used for the point in time `t` value, which is always ordered
  descending in indices. The value 0xFFFFFFFFFFFFFF has 7 bytes, so the first
  byte will be always the zero byte. This comes handy in indices, because the
  zero byte terminates ordering of index segments preceding the `t` value.

  7 bytes are also plenty for the `t` value because with 5 bytes one could carry
  out a transaction every millisecond for 20 years."
  ^long [^long l]
  (bit-and (bit-not l) 0xFFFFFFFFFFFFFF))


(defn t-key [t]
  (-> (ByteBuffer/allocate t-size)
      (.putLong (descending-long t))
      (.array)))


(defn decode-t-key [k]
  (descending-long (.getLong (ByteBuffer/wrap k))))



;; ---- SearchParamValueResource Index ----------------------------------------

(defn hash-prefix ^bytes [^HashCode hash]
  (let [bs (byte-array hash-prefix-size)]
    (.writeBytesTo hash bs 0 hash-prefix-size)
    bs))


(defn sp-value-resource-key
  {:arglists
   '([c-hash tid value]
     [c-hash tid value id]
     [c-hash tid value id hash])}
  ([c-hash tid value]
   (-> (doto (ByteBuffer/allocate (+ c-hash-size tid-size ^int (bs/size value)))
         (.putInt c-hash)
         (.putInt tid)
         (bb/into! value))
       (.array)))
  ([c-hash tid value ^bytes id]
   (-> (doto (ByteBuffer/allocate (+ c-hash-size tid-size ^int (bs/size value) 1
                                     (alength id) 1))
         (.putInt c-hash)
         (.putInt tid)
         (bb/into! value)
         (.put (byte 0))
         (.put id)
         (.put (byte (alength id))))
       (.array)))
  ([c-hash tid value ^bytes id ^bytes hash]
   (-> (doto (ByteBuffer/allocate (+ c-hash-size tid-size ^int (bs/size value) 1
                                     (alength id) 1 hash-prefix-size))
         (.putInt c-hash)
         (.putInt tid)
         (bb/into! value)
         (.put (byte 0))
         (.put id)
         (.put (byte (alength id)))
         (.put (hash-prefix hash)))
       (.array))))


(defn- append-fs [^bytes k ^long n]
  (let [b (byte-array (+ (alength k) n))]
    (System/arraycopy k 0 b 0 (alength k))
    (dotimes [i n]
      (aset b (+ (alength k) i) (byte 0xff)))
    b))


(defn sp-value-resource-key-for-prev
  ([c-hash tid value]
   (append-fs (sp-value-resource-key c-hash tid value)
              (+ 1 max-id-size 1 hash-prefix-size)))
  ([c-hash tid value id]
   (append-fs (sp-value-resource-key c-hash tid value id)
              hash-prefix-size)))


(def ^:private ^:const ^int sp-value-resource-key-buffer-capacity
  "Most search param value keys should fit into this size."
  128)


(defn decode-sp-value-resource-key
  ([] (ByteBuffer/allocateDirect sp-value-resource-key-buffer-capacity))
  ([^ByteBuffer bb]
   (let [id-size (.get bb (dec (- (.limit bb) hash-prefix-size)))
         prefix (byte-array (- (.remaining bb) id-size 2 hash-prefix-size))
         id (byte-array id-size)
         hash-prefix (byte-array hash-prefix-size)]
     (.get bb prefix)
     (.get bb)
     (.get bb id)
     (.get bb)
     (.get bb hash-prefix)
     [prefix id hash-prefix])))


(defn c-hash [code]
  (.asInt (.hashString (Hashing/murmur3_32) ^String code utf-8)))


(def ^:private c-hash->code
  (into
    {}
    (map (fn [code] [(c-hash code) code]))
    ["_id"
     "code"
     "code-value-quantity"
     "combo-code"
     "combo-code-value-quantity"
     "combo-value-quantity"
     "context-quantity"
     "patient"
     "status"
     "subject"
     "value-quantity"]))


(defn decode-sp-value-resource-key-human
  ([] (ByteBuffer/allocateDirect sp-value-resource-key-buffer-capacity))
  ([^ByteBuffer bb]
   (let [id-size (.get bb (dec (- (.limit bb) hash-prefix-size)))
         value-size (- (.remaining bb) id-size 2 hash-prefix-size c-hash-size
                       tid-size)
         c-hash (.getInt bb)
         tid (.getInt bb)
         value (byte-array value-size)
         _ (.get bb value)
         _ (.get bb)
         id (byte-array id-size)
         _ (.get bb id)
         _ (.get bb)
         hash-prefix (byte-array hash-prefix-size)
         _ (.get bb hash-prefix)]
     {:code (or (c-hash->code c-hash) (Integer/toHexString c-hash))
      :type (tid->type tid)
      :value (ByteString/copyFrom value)
      :id (blaze.db.impl.codec/id id)
      :hash-prefix (hex hash-prefix)})))



;; ---- ResourceSearchParamValue Index ----------------------------------------

(defn resource-sp-value-key
  {:arglists
   '([tid id hash c-hash]
     [tid id hash c-hash value])}
  ([tid ^bytes id hash c-hash]
   (-> (ByteBuffer/allocate (+ tid-size 1 (alength id) hash-prefix-size
                               c-hash-size))
       (.putInt tid)
       (.put (byte (alength id)))
       (.put id)
       (.put (if (bytes? hash) ^bytes hash (hash-prefix hash)))
       (.putInt c-hash)
       (.array)))
  ([tid ^bytes id hash c-hash value]
   (-> (doto (ByteBuffer/allocate (+ tid-size 1 (alength id) hash-prefix-size
                                     c-hash-size ^int (bs/size value)))
         (.putInt tid)
         (.put (byte (alength id)))
         (.put id)
         (.put (if (bytes? hash) ^bytes hash (hash-prefix hash)))
         (.putInt c-hash)
         (bb/into! value))
       (.array))))


(def ^:private ^:const ^int resource-sp-value-key-buffer-capacity
  "Most resource value keys should fit into this size."
  128)


(defn decode-resource-sp-value-key
  "Decodes ResourceSearchParamValue keys into a tuple of `[prefix value]`, where
  `prefix` are all bytes before the `value` byte string."
  ([] (ByteBuffer/allocateDirect resource-sp-value-key-buffer-capacity))
  ([^ByteBuffer bb]
   (let [id-size (.get bb (+ (.position bb) tid-size))
         prefix (byte-array (+ tid-size 1 id-size hash-prefix-size c-hash-size))]
     (.get bb prefix)
     [prefix (bs/from-byte-buffer bb)])))


(defn decode-resource-sp-value-key-human
  ([] (ByteBuffer/allocateDirect resource-sp-value-key-buffer-capacity))
  ([^ByteBuffer bb]
   (let [id-size (.get bb (+ (.position bb) tid-size))
         tid (.getInt bb)
         _ (.get bb)
         id (byte-array id-size)
         _ (.get bb id)
         hash-prefix (byte-array hash-prefix-size)
         _ (.get bb hash-prefix)
         c-hash (.getInt bb)
         value (byte-array (.remaining bb))]
     (.get bb value)
     {:type (tid->type tid)
      :id (blaze.db.impl.codec/id id)
      :hash-prefix (hex hash-prefix)
      :code (or (c-hash->code c-hash) (Integer/toHexString c-hash))
      :value (ByteString/copyFrom value)})))


(defn resource-sp-value-key->value [^bytes k]
  (let [bb (ByteBuffer/wrap k)
        id-size (.get bb tid-size)
        prefix-length (+ tid-size 1 id-size hash-prefix-size c-hash-size)]
    (.position bb prefix-length)
    (ByteString/copyFrom bb)))



;; ---- CompartmentResource Index ---------------------------------------------

(defn compartment-search-param-value-key
  {:arglists
   '([co-c-hash co-res-id sp-c-hash tid value]
     [co-c-hash co-res-id sp-c-hash tid value id hash])}
  ([co-c-hash ^bytes co-res-id sp-c-hash tid value]
   (-> (doto (ByteBuffer/allocate (+ c-hash-size (alength co-res-id)
                                     c-hash-size tid-size ^int (bs/size value)))
         (.putInt co-c-hash)
         (.put co-res-id)
         (.putInt sp-c-hash)
         (.putInt tid)
         (bb/into! value))
       (.array)))
  ([co-c-hash ^bytes co-res-id sp-c-hash tid value ^bytes id hash]
   (-> (doto (ByteBuffer/allocate (+ c-hash-size (alength co-res-id)
                                     c-hash-size tid-size ^int (bs/size value) 1
                                     (alength id) 1 hash-prefix-size))
         (.putInt co-c-hash)
         (.put co-res-id)
         (.putInt sp-c-hash)
         (.putInt tid)
         (bb/into! value)
         (.put (byte 0))
         (.put id)
         (.put (byte (alength id)))
         (.put (hash-prefix hash)))
       (.array))))



;; ---- CompartmentResourceValue Index ----------------------------------------

(defn compartment-resource-value-key
  {:arglists
   '([co-c-hash co-res-id tid id hash c-hash]
     [co-c-hash co-res-id tid id hash c-hash value])}
  ([co-c-hash ^bytes co-res-id tid ^bytes id ^bytes hash sp-c-hash]
   (-> (ByteBuffer/allocate (+ c-hash-size (alength co-res-id)
                               tid-size (alength id) hash-prefix-size
                               c-hash-size))
       (.putInt co-c-hash)
       (.put co-res-id)
       (.putInt tid)
       (.put id)
       (.put (hash-prefix hash))
       (.putInt sp-c-hash)
       (.array)))
  ([co-c-hash ^bytes co-res-id tid ^bytes id ^bytes hash sp-c-hash ^bytes value]
   (-> (ByteBuffer/allocate (+ c-hash-size (alength co-res-id)
                               tid-size (alength id) hash-prefix-size
                               c-hash-size (alength value)))
       (.putInt co-c-hash)
       (.put co-res-id)
       (.putInt tid)
       (.put id)
       (.put (hash-prefix hash))
       (.putInt sp-c-hash)
       (.put value)
       (.array))))



;; ---- ResourceType Index ----------------------------------------------------

(defn resource-type-key
  ([tid]
   (-> (ByteBuffer/allocate tid-size)
       (.putInt tid)
       (.array)))
  ([tid ^bytes id]
   (-> (ByteBuffer/allocate (+ tid-size (alength id)))
       (.putInt tid)
       (.put id)
       (.array))))


(defn resource-type-key->tid [k]
  (.getInt (ByteBuffer/wrap k)))


(defn resource-type-key->id [^bytes k]
  (Arrays/copyOfRange k tid-size (alength k)))



;; ---- CompartmentResourceType Index -----------------------------------------

(defn compartment-resource-type-key
  ([co-c-hash ^bytes co-res-id tid]
   (-> (ByteBuffer/allocate (+ c-hash-size 1 (alength co-res-id) tid-size))
       (.putInt co-c-hash)
       (.put (byte (alength co-res-id)))
       (.put co-res-id)
       (.putInt tid)
       (.array)))
  ([co-c-hash ^bytes co-res-id tid ^bytes id]
   (-> (ByteBuffer/allocate (+ c-hash-size 1 (alength co-res-id) tid-size
                               (alength id)))
       (.putInt co-c-hash)
       (.put (byte (alength co-res-id)))
       (.put co-res-id)
       (.putInt tid)
       (.put id)
       (.array))))


(defn compartment-resource-type-key->co-c-hash [k]
  (.getInt (ByteBuffer/wrap k)))


(defn compartment-resource-type-key->co-res-id [^bytes k]
  (let [bb (ByteBuffer/wrap k)
        co-res-id-size (.get bb c-hash-size)
        from (unchecked-inc-int c-hash-size)]
    (Arrays/copyOfRange k from (unchecked-add-int from co-res-id-size))))


(defn compartment-resource-type-key->tid [k]
  (let [bb (ByteBuffer/wrap k)
        co-res-id-size (.get bb c-hash-size)]
    (.getInt bb (+ c-hash-size 1 co-res-id-size))))


(def ^:private ^:const ^int compartment-resource-type-key-id-from
  (+ (unchecked-inc-int c-hash-size) tid-size))


(defn compartment-resource-type-key->id [^bytes k]
  (let [co-res-id-size (.get (ByteBuffer/wrap k) c-hash-size)]
    (Arrays/copyOfRange k (unchecked-add-int compartment-resource-type-key-id-from co-res-id-size) (alength k))))


(def ^:private ^:const ^int max-compartment-resource-type-key-size
  (+ c-hash-size 1 max-id-size tid-size max-id-size))


(defn decode-compartment-resource-type-key
  ([]
   (ByteBuffer/allocateDirect max-compartment-resource-type-key-size))
  ([^ByteBuffer bb]
   (let [co-res-id-size (.get bb c-hash-size)
         prefix (byte-array (+ c-hash-size 1 co-res-id-size tid-size))
         id (byte-array (- (.remaining bb) (alength prefix)))]
     (.get bb prefix)
     (.get bb id)
     [prefix id])))


;; ---- ResourceAsOf Index ----------------------------------------------------


(defn resource-as-of-key
  ([tid]
   (-> (ByteBuffer/allocate tid-size)
       (.putInt tid)
       (.array)))
  ([tid ^bytes id t]
   (-> (ByteBuffer/allocate (+ tid-size (alength id) t-size))
       (.putInt tid)
       (.put id)
       (.putLong (descending-long t))
       (.array))))


(defn resource-as-of-key->tid [k]
  (.getInt (ByteBuffer/wrap k)))


(defn resource-as-of-key->id [^bytes k]
  (Arrays/copyOfRange k tid-size (- (alength k) t-size)))


(defn resource-as-of-key->t ^long [^bytes k]
  (descending-long (.getLong (ByteBuffer/wrap k) (- (alength k) t-size))))


(defn state [num-changes op]
  (cond-> (bit-shift-left ^long num-changes 8)
    (identical? :create op) (bit-set 1)
    (identical? :delete op) (bit-set 0)))


(defn state->num-changes
  "A resource is new if num-changes is 1."
  [state]
  (bit-shift-right ^long state 8))


(defn state->op [state]
  (cond
    (bit-test ^long state 1) :create
    (bit-test ^long state 0) :delete
    :else :put))


(defn deleted? [^long state]
  (= 1 (bit-and state 1)))


(def ^:const ^int resource-as-of-value-size
  (+ hash-size state-size))


(defn resource-as-of-value [hash state]
  (-> (ByteBuffer/allocate resource-as-of-value-size)
      (.put (hash/encode hash))
      (.putLong state)
      (.array)))


(defn resource-as-of-value->hash [^bytes v]
  (HashCode/fromBytes (Arrays/copyOfRange v 0 hash-size)))


(defn resource-as-of-value->state [v]
  (.getLong (ByteBuffer/wrap v) hash-size))


(defn get-tid! ^long [^ByteBuffer buf]
  (.getInt buf))


(defn get-t! ^long [^ByteBuffer buf]
  (descending-long (.getLong buf)))


(defn get-hash! [^ByteBuffer buf]
  (let [hash (byte-array hash-size)]
    (.get buf hash)
    (HashCode/fromBytes hash)))


(defn get-state! ^long [^ByteBuffer buf]
  (.getLong buf))



;; ---- TypeAsOf Index --------------------------------------------------------

(defn type-as-of-key
  ([tid t]
   (-> (ByteBuffer/allocate (+ tid-size t-size))
       (.putInt tid)
       (.putLong (descending-long t))
       (.array)))
  ([tid t ^bytes id]
   (-> (ByteBuffer/allocate (+ tid-size t-size (alength id)))
       (.putInt tid)
       (.putLong (descending-long t))
       (.put id)
       (.array))))


(defn type-as-of-key->tid [k]
  (.getInt (ByteBuffer/wrap k)))


(defn type-as-of-key->t [k]
  (descending-long (.getLong (ByteBuffer/wrap k) tid-size)))


(defn type-as-of-key->id [^bytes k]
  (Arrays/copyOfRange k (+ tid-size t-size) (alength k)))



;; ---- SystemAsOf Index ----------------------------------------------------

(defn system-as-of-key
  ([t]
   (-> (ByteBuffer/allocate t-size)
       (.putLong (descending-long t))
       (.array)))
  ([t tid]
   (-> (ByteBuffer/allocate (+ t-size tid-size))
       (.putLong (descending-long t))
       (.putInt tid)
       (.array)))
  ([t tid ^bytes id]
   (-> (ByteBuffer/allocate (+ t-size tid-size (alength id)))
       (.putLong (descending-long t))
       (.putInt tid)
       (.put id)
       (.array))))


(defn system-as-of-key->t [k]
  (descending-long (.getLong (ByteBuffer/wrap k))))


(defn system-as-of-key->tid [k]
  (.getInt (ByteBuffer/wrap k) t-size))


(defn system-as-of-key->id [^bytes k]
  (Arrays/copyOfRange k (+ t-size tid-size) (alength k)))



;; ---- Other Functions -------------------------------------------------------

(defn v-hash [value]
  (-> (Hashing/murmur3_32)
      (.hashString ^String value utf-8)
      (.asBytes)
      (ByteString/copyFrom)))


(defn tid-id
  "Returns a byte string with tid from `type` followed by `id`."
  [tid ^bytes id]
  (-> (ByteBuffer/allocate (+ tid-size (alength id)))
      (.putInt tid)
      (.put id)
      (.flip)
      (ByteString/copyFrom)))


(defn string
  "Returns a lexicographically sortable byte string of the `string` value."
  [string]
  (ByteString/copyFromUtf8 string))


(defprotocol NumberBytes
  (-number [number]))


(defn number
  "Converts the number in a lexicographically sortable byte string.

  The byte string has variable length."
  [number]
  (-number number))


;; See https://github.com/danburkert/bytekey/blob/6980b9e33281d875f03f4c9a953b93a384eac085/src/encoder.rs#L258
;; And https://cornerwings.github.io/2019/10/lexical-sorting/
(extend-protocol NumberBytes
  BigDecimal
  (-number [val]
    ;; Truncate at two digits after the decimal point
    (-number (.longValue (.scaleByPowerOfTen val 2))))

  Integer
  (-number [val]
    (-number (long val)))

  Long
  (-number [val]
    (let [mask (bit-shift-right ^long val 63)
          val (- (Math/abs ^long val) (bit-and 1 mask))]
      (condp > val
        (bit-shift-left 1 3)
        (-> (ByteBuffer/allocate 1)
            (.put (byte (bit-xor (bit-or val (bit-shift-left 0x10 3)) mask)))
            (.flip)
            (ByteString/copyFrom))

        (bit-shift-left 1 11)
        (-> (ByteBuffer/allocate 2)
            (.putShort (bit-xor (bit-or val (bit-shift-left 0x11 11)) mask))
            (.flip)
            (ByteString/copyFrom))

        (bit-shift-left 1 19)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x12 19)) mask)]
          (-> (ByteBuffer/allocate 3)
              (.put (byte (bit-shift-right masked 16)))
              (.putShort masked)
              (.flip)
              (ByteString/copyFrom)))

        (bit-shift-left 1 27)
        (-> (ByteBuffer/allocate 4)
            (.putInt (bit-xor (bit-or val (bit-shift-left 0x13 27)) mask))
            (.flip)
            (ByteString/copyFrom))

        (bit-shift-left 1 35)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x14 35)) mask)]
          (-> (ByteBuffer/allocate 5)
              (.put (byte (bit-shift-right masked 32)))
              (.putInt masked)
              (.flip)
              (ByteString/copyFrom)))

        (bit-shift-left 1 43)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x15 43)) mask)]
          (-> (ByteBuffer/allocate 6)
              (.putShort (bit-shift-right masked 32))
              (.putInt masked)
              (.flip)
              (ByteString/copyFrom)))

        (bit-shift-left 1 51)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x16 51)) mask)]
          (-> (ByteBuffer/allocate 7)
              (.put (byte (bit-shift-right masked 48)))
              (.putShort (bit-shift-right masked 32))
              (.putInt masked)
              (.flip)
              (ByteString/copyFrom)))

        (bit-shift-left 1 59)
        (-> (ByteBuffer/allocate 8)
            (.putLong (bit-xor (bit-or val (bit-shift-left 0x17 59)) mask))
            (.flip)
            (ByteString/copyFrom))

        (-> (ByteBuffer/allocate 9)
            (.put (byte (bit-xor (bit-shift-left 0x18 3) mask)))
            (.putLong (bit-xor val mask))
            (.flip)
            (ByteString/copyFrom))))))


(defn- epoch-seconds ^long [^LocalDateTime date-time ^ZoneId zone-id]
  (.toEpochSecond (.atZone date-time zone-id)))


(def ^:private ^:const ^long ub-offset 0xf0000000000)


(defprotocol DateLowerBound
  (-date-lb [date-time zone-id]))


(extend-protocol DateLowerBound
  Year
  (-date-lb [year zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay year 1)) zone-id)))
  DateTimeYear
  (-date-lb [year zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay ^Year (.year year) 1)) zone-id)))
  YearMonth
  (-date-lb [year-month zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay year-month 1)) zone-id)))
  DateTimeYearMonth
  (-date-lb [year-month zone-id]
    (number (epoch-seconds (.atStartOfDay (.atDay ^YearMonth (.yearMonth year-month) 1)) zone-id)))
  LocalDate
  (-date-lb [date zone-id]
    (number (epoch-seconds (.atStartOfDay date) zone-id)))
  DateTimeYearMonthDay
  (-date-lb [date zone-id]
    (number (epoch-seconds (.atStartOfDay ^LocalDate (.date date)) zone-id)))
  LocalDateTime
  (-date-lb [date-time zone-id]
    (number (epoch-seconds date-time zone-id)))
  OffsetDateTime
  (-date-lb [date-time _]
    (number (.toEpochSecond date-time))))


(defn date-lb
  "Returns the lower bound of the implicit range the `date-time` value spans."
  ^bytes [zone-id date-time]
  (-date-lb date-time zone-id))


(def ^:private ^:const ^long ub-first-byte 0xb0)


(defn date-lb?
  "Tests whether `bs` starting at `offset` represent a date lower bound."
  [bs offset]
  (< (bit-and ^long (bs/nth bs offset) 0xff) ub-first-byte))


(defn date-ub?
  "Tests whether `bs` starting at `offset` represent a date upper bound."
  [bs offset]
  (>= (bit-and ^long (bs/nth bs offset) 0xff) ub-first-byte))


(defprotocol DateUpperBound
  (-date-ub [date-time zone-id]))


(extend-protocol DateUpperBound
  Year
  (-date-ub [year zone-id]
    (number (+ ub-offset (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears year 1) 1)) zone-id)))))
  DateTimeYear
  (-date-ub [year zone-id]
    (number (+ ub-offset (dec (epoch-seconds (.atStartOfDay (.atDay (.plusYears ^Year (.year year) 1) 1)) zone-id)))))
  YearMonth
  (-date-ub [year-month zone-id]
    (number (+ ub-offset (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths year-month 1) 1)) zone-id)))))
  DateTimeYearMonth
  (-date-ub [year-month zone-id]
    (number (+ ub-offset (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths ^YearMonth (.yearMonth year-month) 1) 1)) zone-id)))))
  LocalDate
  (-date-ub [date zone-id]
    (number (+ ub-offset (dec (epoch-seconds (.atStartOfDay (.plusDays date 1)) zone-id)))))
  DateTimeYearMonthDay
  (-date-ub [date zone-id]
    (number (+ ub-offset (dec (epoch-seconds (.atStartOfDay (.plusDays ^LocalDate (.date date) 1)) zone-id)))))
  LocalDateTime
  (-date-ub [date-time zone-id]
    (number (+ ub-offset (epoch-seconds date-time zone-id))))
  OffsetDateTime
  (-date-ub [date-time _]
    (number (+ ub-offset (.toEpochSecond date-time)))))


(defn date-ub
  "Returns the upper bound of the implicit range the `date-time` value spans."
  ^bytes [zone-id date-time]
  (-date-ub date-time zone-id))


(def date-min-bound
  (date-lb (ZoneOffset/ofHours 0) (Year/of 1)))


(def date-max-bound
  (date-ub (ZoneOffset/ofHours 0) (Year/of 9999)))


(defn date-lb-ub [lb ub]
  (-> (doto (ByteBuffer/allocate (+ 1 ^int (bs/size lb) ^int (bs/size ub)))
        (.put (byte (bs/size lb)))
        (bb/into! lb)
        (bb/into! ub))
      (.flip)
      (ByteString/copyFrom)))


(defn date-lb-ub->lb [lb-ub]
  (bs/subs lb-ub 1 (inc ^long (bs/nth lb-ub 0))))


(defn date-lb-ub->ub [lb-ub]
  (bs/subs lb-ub (inc ^long (bs/nth lb-ub 0)) (bs/size lb-ub)))


(defn quantity [unit value]
  (bs/concat (v-hash (or unit "")) (number value)))


(defn deleted-resource [type id]
  {:fhir/type (keyword "fhir" type) :id id})



;; ---- Transaction -----------------------------------------------------------
;; TODO: encode nanoseconds here?

(defn tx-by-instant-key [instant]
  (-> (ByteBuffer/allocate tx-time-size)
      (.putLong (descending-long (inst-ms instant)))
      (.array)))


(defn encode-t [t]
  (-> (ByteBuffer/allocate t-size)
      (.putLong t)
      (.array)))


(defn decode-t [bs]
  (.getLong (ByteBuffer/wrap bs)))


(defn encode-tx [{tx-instant :blaze.db.tx/instant}]
  (cheshire/generate-cbor {:inst (inst-ms tx-instant)}))


(defn decode-tx [bytes t]
  (let [{:keys [inst]} (cheshire/parse-cbor bytes keyword)]
    {:blaze.db/t t
     :blaze.db.tx/instant (Instant/ofEpochMilli inst)}))


(defn tx-success-entries [t tx-instant]
  [[:tx-success-index (t-key t) (encode-tx {:blaze.db.tx/instant tx-instant})]
   [:t-by-instant-index (tx-by-instant-key tx-instant) (encode-t t)]])


(defn encode-tx-error [{::anom/keys [category message] :http/keys [status]}]
  (cheshire/generate-cbor
    {:category (name category) :message message :http-status status}))


(defn decode-tx-error
  "Returns an anomaly."
  [bytes]
  (let [{:keys [category message http-status]} (cheshire/parse-cbor bytes keyword)]
    (cond->
      {::anom/category (keyword "cognitect.anomalies" category)
       ::anom/message message}
      http-status
      (assoc :http/status http-status))))


(defn tx-error-entries [t anomaly]
  [[:tx-error-index (t-key t) (encode-tx-error anomaly)]])

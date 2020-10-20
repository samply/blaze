(ns blaze.db.impl.codec
  (:require
    [blaze.fhir.hash :as hash]
    [cheshire.core :as cheshire]
    [cognitect.anomalies :as anom])
  (:import
    [blaze.fhir.spec.type.system DateTimeYear DateTimeYearMonth
                                 DateTimeYearMonthDay]
    [com.github.benmanes.caffeine.cache CacheLoader Caffeine]
    [com.google.common.hash HashCode Hashing]
    [com.google.common.io BaseEncoding]
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



;; ---- SearchParamValue Index ------------------------------------------------

(defn- hash-prefix ^bytes [^HashCode hash]
  (let [bs (byte-array hash-prefix-size)]
    (.writeBytesTo hash bs 0 hash-prefix-size)
    bs))


(defn search-param-value-key
  {:arglists
   '([c-hash tid value]
     [c-hash tid value id]
     [c-hash tid value id hash])}
  ([c-hash tid ^bytes value]
   (-> (ByteBuffer/allocate (+ c-hash-size tid-size (alength value)))
       (.putInt c-hash)
       (.putInt tid)
       (.put value)
       (.array)))
  ([c-hash tid ^bytes value ^bytes id]
   (-> (ByteBuffer/allocate (+ c-hash-size tid-size (alength value) 1
                               (alength id) 1))
       (.putInt c-hash)
       (.putInt tid)
       (.put value)
       (.put (byte 0))
       (.put id)
       (.put (byte (alength id)))
       (.array)))
  ([c-hash tid ^bytes value ^bytes id ^bytes hash]
   (-> (ByteBuffer/allocate (+ c-hash-size tid-size (alength value) 1
                               (alength id) 1 hash-prefix-size))
       (.putInt c-hash)
       (.putInt tid)
       (.put value)
       (.put (byte 0))
       (.put id)
       (.put (byte (alength id)))
       (.put (hash-prefix hash))
       (.array))))


(defn append-fs [^bytes k ^long n]
  (let [b (byte-array (+ (alength k) n))]
    (System/arraycopy k 0 b 0 (alength k))
    (dotimes [i n]
      (aset b (+ (alength k) i) (byte 0xff)))
    b))


(defn search-param-value-key-for-prev
  [c-hash tid value]
  (append-fs (search-param-value-key c-hash tid value)
             (+ 1 max-id-size 1 hash-prefix-size)))


(defn decode-search-param-value-key
  ([] (ByteBuffer/allocateDirect 1024))
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



;; ---- ResourceValue Index ---------------------------------------------------

(defn resource-value-key
  {:arglists '([tid id hash c-hash] [tid id hash c-hash value])}
  ([tid ^bytes id hash c-hash]
   (-> (ByteBuffer/allocate (+ tid-size 1 (alength id) hash-prefix-size
                               c-hash-size))
       (.putInt tid)
       (.put (byte (alength id)))
       (.put id)
       (.put (if (bytes? hash) ^bytes hash (hash-prefix hash)))
       (.putInt c-hash)
       (.array)))
  ([tid ^bytes id hash c-hash ^bytes value]
   (-> (ByteBuffer/allocate (+ tid-size 1 (alength id) hash-prefix-size
                               c-hash-size (alength value)))
       (.putInt tid)
       (.put (byte (alength id)))
       (.put id)
       (.put (if (bytes? hash) ^bytes hash (hash-prefix hash)))
       (.putInt c-hash)
       (.put value)
       (.array))))


(defn decode-resource-value-key
  ([] (ByteBuffer/allocateDirect 1024))
  ([^ByteBuffer bb]
   (let [id-size (.get bb (+ (.position bb) tid-size))
         prefix (byte-array (+ tid-size 1 id-size hash-prefix-size c-hash-size))
         value (byte-array (- (.remaining bb) (alength prefix)))]
     (.get bb prefix)
     (.get bb value)
     [prefix value])))



;; ---- CompartmentResource Index ---------------------------------------------

(defn compartment-search-param-value-key
  {:arglists
   '([co-c-hash co-res-id sp-c-hash tid value]
     [co-c-hash co-res-id sp-c-hash tid value id hash])}
  ([co-c-hash ^bytes co-res-id sp-c-hash tid ^bytes value]
   (-> (ByteBuffer/allocate (+ c-hash-size (alength co-res-id)
                               c-hash-size tid-size (alength value)))
       (.putInt co-c-hash)
       (.put co-res-id)
       (.putInt sp-c-hash)
       (.putInt tid)
       (.put value)
       (.array)))
  ([co-c-hash ^bytes co-res-id sp-c-hash tid ^bytes value ^bytes id hash]
   (-> (ByteBuffer/allocate (+ c-hash-size (alength co-res-id)
                               c-hash-size tid-size (alength value) 1
                               (alength id) 1 hash-prefix-size))
       (.putInt co-c-hash)
       (.put co-res-id)
       (.putInt sp-c-hash)
       (.putInt tid)
       (.put value)
       (.put (byte 0))
       (.put id)
       (.put (byte (alength id)))
       (.put (hash-prefix hash))
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


(def ^:const ^int compartment-resource-type-key-id-from
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


(defn get-t!
  (^long [^ByteBuffer buf]
   (descending-long (.getLong buf)))
  (^long [^ByteBuffer buf ^long index]
   (descending-long (.getLong buf index))))


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


(defn c-hash [code]
  (.asInt (.hashString (Hashing/murmur3_32) ^String code utf-8)))


(defn v-hash [value]
  (.asBytes (.hashString (Hashing/murmur3_32) ^String value utf-8)))


(defn tid-id
  "Returns a byte array with tid from `type` followed by `id`."
  [tid ^bytes id]
  (let [bb (ByteBuffer/allocate (+ tid-size (alength id)))]
    (.putInt bb tid)
    (.put bb id)
    (.array bb)))


(defn string
  "Returns a lexicographically sortable byte string of the `string` value."
  [string]
  (.getBytes ^String string utf-8))


(defprotocol NumberBytes
  (-number [number]))


(defn number
  "Converts the number in a lexicographically sortable byte array.

  The array has variable length."
  [number]
  (-number number))


;; See https://github.com/danburkert/bytekey/blob/6980b9e33281d875f03f4c9a953b93a384eac085/src/encoder.rs#L258
;; And https://cornerwings.github.io/2019/10/lexical-sorting/
(extend-protocol NumberBytes
  BigDecimal
  (-number [val]
    (let [^bytes long-bs (number (.longValueExact (.toBigInteger val)))
          bb (ByteBuffer/allocate (inc (alength long-bs)))
          scale (.scale val)]
      (assert (< scale 128))
      (.put bb (byte scale))
      (.put bb long-bs)
      (.array bb)))

  Integer
  (-number [val]
    (number (long val)))

  Long
  (-number [val]
    (let [mask (bit-shift-right ^long val 63)
          val (- (Math/abs ^long val) (bit-and 1 mask))]
      (condp > val
        (bit-shift-left 1 3)
        (let [bb (ByteBuffer/allocate 1)]
          (.put bb (byte (bit-xor (bit-or val (bit-shift-left 0x10 3)) mask)))
          (.array bb))

        (bit-shift-left 1 11)
        (let [bb (ByteBuffer/allocate 2)]
          (.putShort bb (bit-xor (bit-or val (bit-shift-left 0x11 11)) mask))
          (.array bb))

        (bit-shift-left 1 19)
        (let [bb (ByteBuffer/allocate 3)
              masked (bit-xor (bit-or val (bit-shift-left 0x12 19)) mask)]
          (.put bb (byte (bit-shift-right masked 16)))
          (.putShort bb masked)
          (.array bb))

        (bit-shift-left 1 27)
        (let [bb (ByteBuffer/allocate 4)]
          (.putInt bb (bit-xor (bit-or val (bit-shift-left 0x13 27)) mask))
          (.array bb))

        (bit-shift-left 1 35)
        (let [bb (ByteBuffer/allocate 5)
              masked (bit-xor (bit-or val (bit-shift-left 0x14 35)) mask)]
          (.put bb (byte (bit-shift-right masked 32)))
          (.putInt bb masked)
          (.array bb))

        (bit-shift-left 1 43)
        (let [bb (ByteBuffer/allocate 6)
              masked (bit-xor (bit-or val (bit-shift-left 0x15 43)) mask)]
          (.putShort bb (bit-shift-right masked 32))
          (.putInt bb masked)
          (.array bb))

        (bit-shift-left 1 51)
        (let [bb (ByteBuffer/allocate 7)
              masked (bit-xor (bit-or val (bit-shift-left 0x16 51)) mask)]
          (.put bb (byte (bit-shift-right masked 48)))
          (.putShort bb (bit-shift-right masked 32))
          (.putInt bb masked)
          (.array bb))

        (bit-shift-left 1 59)
        (let [bb (ByteBuffer/allocate 8)]
          (.putLong bb (bit-xor (bit-or val (bit-shift-left 0x17 59)) mask))
          (.array bb))

        (let [bb (ByteBuffer/allocate 9)]
          (.put bb (byte (bit-xor (bit-shift-left 0x18 3) mask)))
          (.putLong bb (bit-xor val mask))
          (.array bb))))))


(defn- epoch-seconds ^long [^LocalDateTime date-time ^ZoneId zone-id]
  (.toEpochSecond (.atZone date-time zone-id)))


(def ^:const ^:private ^long ub-offset 0xf0000000000)


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
  "Tests whether the bytes in `b` starting at `offset` represent a date lower
  bound."
  [^bytes b offset]
  (< (bit-and (aget b offset) 0xff) ub-first-byte))


(defn date-ub?
  "Tests whether the bytes in `b` starting at `offset` represent a date upper
  bound."
  [^bytes b offset]
  (>= (bit-and (aget b offset) 0xff) ub-first-byte))


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


(def date-min-bound (date-lb (ZoneOffset/ofHours 0) (Year/of 1)))


(def date-max-bound (date-ub (ZoneOffset/ofHours 0) (Year/of 9999)))


(defn date-lb-ub [^bytes lb ^bytes ub]
  (let [bb (ByteBuffer/allocate (+ 1 (alength lb) (alength ub)))]
    (.put bb (byte (alength lb)))
    (.put bb lb)
    (.put bb ub)
    (.array bb)))


(defn date-lb-ub->lb [^bytes lb-ub]
  (Arrays/copyOfRange lb-ub 1 (inc (aget lb-ub 0))))


(defn date-lb-ub->ub [^bytes lb-ub]
  (Arrays/copyOfRange lb-ub (inc (aget lb-ub 0)) (alength lb-ub)))


(defn quantity
  ([value]
   (quantity value nil))
  ([value unit]
   (let [unit-hash (v-hash (or unit ""))
         ^bytes number (number value)
         value (byte-array (+ v-hash-size (alength number)))]
     (System/arraycopy unit-hash 0 value 0 v-hash-size)
     (System/arraycopy number 0 value v-hash-size (alength number))
     value)))


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

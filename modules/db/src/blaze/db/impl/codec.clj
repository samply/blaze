(ns blaze.db.impl.codec
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.fhir.spec.type.system]
    [blaze.fhir.util :as fhir-util])
  (:import
    [blaze.fhir.spec.type.system DateTimeYear DateTimeYearMonth
                                 DateTimeYearMonthDay]
    [com.github.benmanes.caffeine.cache CacheLoader Caffeine]
    [com.google.common.hash Hashing]
    [java.nio.charset StandardCharsets]
    [java.time LocalDate LocalDateTime OffsetDateTime Year YearMonth
               ZoneId ZoneOffset]
    [java.util Arrays]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)



;; ---- Sizes of Byte Arrays --------------------------------------------------

(def ^:const ^long hash-size 32)
(def ^:const ^long hash-prefix-size 4)
(def ^:const ^long c-hash-size Integer/BYTES)
(def ^:const ^long v-hash-size Integer/BYTES)
(def ^:const ^long tid-size Integer/BYTES)
(def ^:const ^long t-size Long/BYTES)
(def ^:const ^long state-size Long/BYTES)
(def ^:const ^long tx-time-size Long/BYTES)
(def ^:const ^long max-id-size 64)



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


(def ^{:arglists '([type])} tid
  "Internal type identifier.

  Returns an integer."
  (memoize-1
    (fn [type]
      (-> (Hashing/murmur3_32)
          (.hashBytes (.getBytes ^String type StandardCharsets/ISO_8859_1))
          (.asInt)))))


(let [kvs (->> (fhir-util/resources)
               (map (fn [{:keys [type]}] [(tid type) type]))
               (sort-by first))
      tid->idx (int-array (map first kvs))
      idx->type (object-array (map second kvs))]
  (defn tid->type [tid]
    (let [idx (Arrays/binarySearch tid->idx ^long tid)]
      (when (nat-int? idx)
        (aget idx->type idx)))))



;; ---- Identifier Functions --------------------------------------------------

(defn id-byte-string [id]
  (bs/from-string id StandardCharsets/ISO_8859_1))


(defn id-string [id-byte-string]
  (bs/to-string id-byte-string StandardCharsets/ISO_8859_1))


(defn id
  [^bytes id-bytes ^long offset ^long length]
  (String. id-bytes offset length StandardCharsets/ISO_8859_1))



;; ---- Key Functions ---------------------------------------------------------

(defn descending-long
  "Converts positive longs so that they decrease from 0xFFFFFFFFFFFFFF.

  This function is used for the point in time `t` value, which is always ordered
  descending in indices. The value 0xFFFFFFFFFFFFFF has 7 bytes, so the first
  byte will be always the zero byte. This comes handy in indices, because the
  zero byte terminates ordering of index segments preceding the `t` value.

  7 bytes are also plenty for the `t` value because with 5 bytes one could carry
  out a transaction every millisecond for 20 years."
  {:inline
   (fn [l]
     `(bit-and (bit-not ~l) 0xFFFFFFFFFFFFFF))}
  [l]
  (bit-and (bit-not ^long l) 0xFFFFFFFFFFFFFF))


(defn hash-prefix [hash]
  (bs/subs hash 0 hash-prefix-size))


(defn c-hash [code]
  (-> (Hashing/murmur3_32)
      (.hashString ^String code StandardCharsets/UTF_8)
      (.asInt)))


(def c-hash->code
  (into
    {}
    (map (fn [code] [(c-hash code) code]))
    ["_id"
     "_profile"
     "address"
     "birthdate"
     "bodysite"
     "category"
     "class"
     "code"
     "code-value-quantity"
     "combo-code"
     "combo-code-value-quantity"
     "combo-value-quantity"
     "context-quantity"
     "date"
     "death-date"
     "deceased"
     "description"
     "identifier"
     "issued"
     "item"
     "item:identifier"
     "onset-date"
     "patient"
     "phonetic"
     "series"
     "status"
     "subject"
     "url"
     "value-quantity"
     "version"]))


(defn v-hash [value]
  (-> (Hashing/murmur3_32)
      (.hashString ^String value StandardCharsets/UTF_8)
      (.asBytes)
      (bs/from-byte-array)))


(defn tid-id
  "Returns a byte string with `tid` followed by `id`."
  [tid id]
  (-> (bb/allocate (+ tid-size (bs/size id)))
      (bb/put-int! tid)
      (bb/put-byte-string! id)
      (bb/flip!)
      (bs/from-byte-buffer)))


(defn string
  "Returns a lexicographically sortable byte string of the `string` value."
  [string]
  (bs/from-utf8-string string))


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
        (-> (bb/allocate 1)
            (bb/put-byte! (bit-xor (bit-or val (bit-shift-left 0x10 3)) mask))
            (bb/flip!)
            (bs/from-byte-buffer))

        (bit-shift-left 1 11)
        (-> (bb/allocate 2)
            (bb/put-short! (bit-xor (bit-or val (bit-shift-left 0x11 11)) mask))
            (bb/flip!)
            (bs/from-byte-buffer))

        (bit-shift-left 1 19)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x12 19)) mask)]
          (-> (bb/allocate 3)
              (bb/put-byte! (bit-shift-right masked 16))
              (bb/put-short! masked)
              (bb/flip!)
              (bs/from-byte-buffer)))

        (bit-shift-left 1 27)
        (-> (bb/allocate 4)
            (bb/put-int! (bit-xor (bit-or val (bit-shift-left 0x13 27)) mask))
            (bb/flip!)
            (bs/from-byte-buffer))

        (bit-shift-left 1 35)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x14 35)) mask)]
          (-> (bb/allocate 5)
              (bb/put-byte! (bit-shift-right masked 32))
              (bb/put-int! masked)
              (bb/flip!)
              (bs/from-byte-buffer)))

        (bit-shift-left 1 43)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x15 43)) mask)]
          (-> (bb/allocate 6)
              (bb/put-short! (bit-shift-right masked 32))
              (bb/put-int! masked)
              (bb/flip!)
              (bs/from-byte-buffer)))

        (bit-shift-left 1 51)
        (let [masked (bit-xor (bit-or val (bit-shift-left 0x16 51)) mask)]
          (-> (bb/allocate 7)
              (bb/put-byte! (bit-shift-right masked 48))
              (bb/put-short! (bit-shift-right masked 32))
              (bb/put-int! masked)
              (bb/flip!)
              (bs/from-byte-buffer)))

        (bit-shift-left 1 59)
        (-> (bb/allocate 8)
            (bb/put-long! (bit-xor (bit-or val (bit-shift-left 0x17 59)) mask))
            (bb/flip!)
            (bs/from-byte-buffer))

        (-> (bb/allocate 9)
            (bb/put-byte! (bit-xor (bit-shift-left 0x18 3) mask))
            (bb/put-long! (bit-xor val mask))
            (bb/flip!)
            (bs/from-byte-buffer))))))


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
    (number (epoch-seconds (.atStartOfDay (.atDay ^YearMonth (.-year_month year-month) 1)) zone-id)))
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
  [zone-id date-time]
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
    (number (+ ub-offset (dec (epoch-seconds (.atStartOfDay (.atDay (.plusMonths ^YearMonth (.-year_month year-month) 1) 1)) zone-id)))))
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
  [zone-id date-time]
  (-date-ub date-time zone-id))


(def date-min-bound
  (date-lb (ZoneOffset/ofHours 0) (Year/of 1)))


(def date-max-bound
  (date-ub (ZoneOffset/ofHours 0) (Year/of 9999)))


(defn date-lb-ub [lb ub]
  (-> (bb/allocate (+ 1 (bs/size lb) (bs/size ub)))
      (bb/put-byte! (bs/size lb))
      (bb/put-byte-string! lb)
      (bb/put-byte-string! ub)
      (bb/flip!)
      (bs/from-byte-buffer)))


(defn date-lb-ub->lb [lb-ub]
  (bs/subs lb-ub 1 (inc ^long (bs/nth lb-ub 0))))


(defn date-lb-ub->ub [lb-ub]
  (bs/subs lb-ub (inc ^long (bs/nth lb-ub 0)) (bs/size lb-ub)))


(defn quantity [unit value]
  (bs/concat (v-hash (or unit "")) (number value)))


(defn deleted-resource [type id]
  {:fhir/type (keyword "fhir" type) :id id})

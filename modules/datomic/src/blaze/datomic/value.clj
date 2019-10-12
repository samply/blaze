(ns blaze.datomic.value
  "Read/write for possibly polymorphic values."
  (:require
    [blaze.datomic.quantity :refer [quantity format-unit]])
  (:import
    [java.nio ByteBuffer]
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [java.lang.reflect Array]
    [java.nio.charset Charset]
    [javax.measure Quantity])
  (:refer-clojure :exclude [read]))


(set! *warn-on-reflection* true)


(defprotocol Read
  "Deserialization of byte arrays to types like dates and quantities."
  (read [x]))


(defprotocol Write
  "Serialization of types like dates and quantities into a byte array."
  (write [x]))


(def ^:private ^:const ^byte year-code (byte 0))
(def ^:private ^:const ^byte year-month-code (byte 1))
(def ^:private ^:const ^byte date-code (byte 2))
(def ^:private ^:const ^byte time-code (byte 3))
(def ^:private ^:const ^byte local-date-time-code (byte 4))
(def ^:private ^:const ^byte offset-date-time-code (byte 5))
(def ^:private ^:const ^byte decimal-byte-code (byte 6))
(def ^:private ^:const ^byte decimal-short-code (byte 7))
(def ^:private ^:const ^byte decimal-int-code (byte 8))
(def ^:private ^:const ^byte decimal-long-code (byte 9))
(def ^:private ^:const ^byte decimal-big-int-code (byte 10))
(def ^:private ^:const ^byte nil-code (byte 11))
(def ^:private ^:const ^byte quantity-code (byte 12))
(def ^:private ^:const ^byte string-byte-len-code (byte 13))
(def ^:private ^:const ^byte string-short-len-code (byte 14))
(def ^:private ^:const ^byte string-int-len-code (byte 15))
(def ^:private ^:const ^byte boolean-code (byte 16))
(def ^:private ^:const ^byte bytes-code (byte 17))

(def ^:private ^Charset utf-8 (Charset/forName "utf8"))


(defn- read* [^ByteBuffer bb]
  (case (.get bb)
    0
    (Year/of (.getShort bb))
    1
    (YearMonth/of (.getShort bb) (.get bb))
    2
    (LocalDate/of (.getShort bb) (.get bb) (.get bb))
    3
    (LocalTime/of (.get bb) (.get bb) (.get bb))
    4
    (LocalDateTime/of (.getShort bb) (.get bb) (.get bb)
                      (.get bb) (.get bb) (.get bb))
    5
    (OffsetDateTime/of (.getShort bb) (.get bb) (.get bb)
                       (.get bb) (.get bb) (.get bb)
                       0 (ZoneOffset/ofTotalSeconds (* (.getShort bb) 60)))

    ;; byte-valued decimal with scale
    6
    (BigDecimal/valueOf (.get bb) (.get bb))

    ;; Short-valued decimal with scale
    7
    (BigDecimal/valueOf (.getShort bb) (.get bb))

    ;; Integer-valued decimal with scale
    8
    (BigDecimal/valueOf (.getInt bb) (.get bb))

    ;; Long-valued decimal with scale
    9
    (BigDecimal/valueOf (.getLong bb) (.get bb))

    ;; BigInteger-valued decimal with scale
    10
    (let [big-int-bytes (byte-array (- (.limit bb) 2))]
      (.get bb big-int-bytes)
      (BigDecimal. (BigInteger. big-int-bytes) (.get bb)))

    11
    nil

    12
    (quantity (read* bb) (read* bb))

    13
    (let [bytes (byte-array (.get bb))]
      (.get bb bytes)
      (String. bytes utf-8))

    16
    (if (= 1 (.get bb)) true false)

    17
    (let [bytes (byte-array (.getInt bb))]
      (.get bb bytes)
      bytes)))


(extend-type
  (Class/forName "[B")
  Read
  (read [bytes] (read* (ByteBuffer/wrap bytes))))


;; Objects and nil read itself back
(extend-protocol Read
  Object
  (read [x] x)
  nil
  (read [_]))


(extend-protocol Write
  (Class/forName "[B")
  (write [bytes]
    (let [length (Array/getLength bytes)]
      (-> (doto (ByteBuffer/allocate (+ length 5))
            (.put bytes-code)
            (.putInt length)
            (.put ^bytes bytes))
          (.array))))

  Year
  (write [this]
    (-> (doto (ByteBuffer/allocate 3)
          (.put year-code)
          (.putShort (.getValue this)))
        (.array)))

  YearMonth
  (write [this]
    (-> (doto (ByteBuffer/allocate 4)
          (.put year-month-code)
          (.putShort (.getYear this))
          (.put (byte (.getMonthValue this))))
        (.array)))

  LocalDate
  (write [this]
    (-> (doto (ByteBuffer/allocate 5)
          (.put date-code)
          (.putShort (.getYear this))
          (.put (byte (.getMonthValue this)))
          (.put (byte (.getDayOfMonth this))))
        (.array)))

  LocalTime
  (write [this]
    (-> (doto (ByteBuffer/allocate 4)
          (.put time-code)
          (.put (byte (.getHour this)))
          (.put (byte (.getMinute this)))
          (.put (byte (.getSecond this))))
        (.array)))

  LocalDateTime
  (write [this]
    (-> (doto (ByteBuffer/allocate 8)
          (.put local-date-time-code)
          (.putShort (.getYear this))
          (.put (byte (.getMonthValue this)))
          (.put (byte (.getDayOfMonth this)))
          (.put (byte (.getHour this)))
          (.put (byte (.getMinute this)))
          (.put (byte (.getSecond this))))
        (.array)))

  OffsetDateTime
  (write [this]
    (-> (doto (ByteBuffer/allocate 10)
          (.put offset-date-time-code)
          (.putShort (.getYear this))
          (.put (byte (.getMonthValue this)))
          (.put (byte (.getDayOfMonth this)))
          (.put (byte (.getHour this)))
          (.put (byte (.getMinute this)))
          (.put (byte (.getSecond this)))
          (.putShort (quot (.getTotalSeconds (.getOffset this)) 60)))
        (.array)))

  BigDecimal
  (write [d]
    (let [unscaled-val (.unscaledValue d)]
      (condp > (.bitLength unscaled-val)
        8
        (-> (doto (ByteBuffer/allocate 3)
              (.put decimal-byte-code)
              (.put (.byteValueExact unscaled-val))
              (.put (byte (.scale d))))
            (.array))
        16
        (-> (doto (ByteBuffer/allocate 4)
              (.put decimal-short-code)
              (.putShort (.shortValueExact unscaled-val))
              (.put (byte (.scale d))))
            (.array))
        32
        (-> (doto (ByteBuffer/allocate 6)
              (.put decimal-int-code)
              (.putInt (.intValueExact unscaled-val))
              (.put (byte (.scale d))))
            (.array))
        64
        (-> (doto (ByteBuffer/allocate 10)
              (.put decimal-long-code)
              (.putLong (.longValueExact unscaled-val))
              (.put (byte (.scale d))))
            (.array))
        (let [bytes (.toByteArray unscaled-val)]
          (-> (doto (ByteBuffer/allocate (+ (Array/getLength bytes) 2))
                (.put decimal-big-int-code)
                (.put bytes)
                (.put (byte (.scale d))))
              (.array))))))

  nil
  (write [_]
    (byte-array [nil-code]))

  Quantity
  (write [q]
    (let [^bytes value-bytes (write (.getValue q))
          ^bytes unit-bytes (write (format-unit (.getUnit q)))]
      (-> (doto (ByteBuffer/allocate (+ (Array/getLength value-bytes) (Array/getLength unit-bytes) 1))
            (.put quantity-code)
            (.put value-bytes)
            (.put unit-bytes))
          (.array))))

  String
  (write [s]
    (let [bytes (.getBytes s utf-8)
          length (Array/getLength bytes)]
      (condp > length
        128
        (-> (doto (ByteBuffer/allocate (+ length 2))
              (.put string-byte-len-code)
              (.put (byte length))
              (.put bytes))
            (.array)))))

  Boolean
  (write [b]
    (-> (doto (ByteBuffer/allocate 2)
          (.put (byte 16))
          (.put (byte (if b 1 0))))
        (.array))))


(comment
  (let [bytes (write (Year/of 2012))]
    (criterium.core/quick-bench (read bytes)))

  (criterium.core/quick-bench (Year/parse "2012"))

  (let [bytes (write (YearMonth/of 2012 1))]
    (criterium.core/quick-bench (read bytes)))

  (criterium.core/quick-bench (YearMonth/parse "2012-01"))

  (let [bytes (write (LocalDate/of 2012 2 29))]
    (criterium.core/quick-bench (read bytes)))

  (criterium.core/quick-bench (LocalDate/parse "2012-02-29"))

  (let [bytes (write (LocalDateTime/of 2012 2 29 12 13 14))]
    (criterium.core/quick-bench (read bytes)))

  (criterium.core/quick-bench (LocalDateTime/parse "2012-02-29T12:13:14"))

  (let [bytes (write (LocalTime/of 12 13 14))]
    (criterium.core/quick-bench (read bytes)))

  (criterium.core/quick-bench (LocalTime/parse "12:13:14"))

  (let [bytes (write (OffsetDateTime/of 2012 2 29 12 13 14 0 (ZoneOffset/ofHours 1)))]
    (criterium.core/quick-bench (read bytes)))

  (criterium.core/quick-bench (OffsetDateTime/parse "2012-02-29T12:13:14+01:00"))

  (let [bytes (write 42.23M)]
    (criterium.core/bench (read bytes)))

  (def bytes (write (quantity 1M "kg")))
  (count bytes)

  (criterium.core/bench (read bytes))

  (format-unit (.getUnit (quantity 1M "nm")))

  (read (write (Year/of 2012)))
  (read (write (YearMonth/of 2012 2)))
  (read (write (LocalDate/of 2012 2 29)))
  (read (write (LocalTime/of 12 13 14)))
  (read (write (LocalDateTime/of 2012 2 29 12 13 14)))
  (read (write (OffsetDateTime/of 2012 2 29 12 13 14 0 (ZoneOffset/ofHours 1))))

  )

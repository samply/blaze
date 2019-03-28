(ns life-fhir-store.datomic.time
  "FHIR date, dateTime and time serialization in all allowed precisions
  (xs:dateTime, xs:date, xs:gYearMonth and xs:gYear).

  Uses java.time.LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
  classes.

  Use `read` and `write` functions."
  (:import
    [java.nio ByteBuffer]
    [java.time LocalDate LocalDateTime LocalTime OffsetDateTime Year YearMonth
               ZoneOffset])
  (:refer-clojure :exclude [read]))


(defprotocol Write
  (write [this]))


(extend-protocol Write
  Year
  (write [this]
    (-> (doto (ByteBuffer/allocate 3)
          (.put (byte 0))
          (.putShort (.getValue this)))
        (.array)))

  YearMonth
  (write [this]
    (-> (doto (ByteBuffer/allocate 4)
          (.put (byte 1))
          (.putShort (.getYear this))
          (.put (byte (.getMonthValue this))))
        (.array)))

  LocalDate
  (write [this]
    (-> (doto (ByteBuffer/allocate 5)
          (.put (byte 2))
          (.putShort (.getYear this))
          (.put (byte (.getMonthValue this)))
          (.put (byte (.getDayOfMonth this))))
        (.array)))

  LocalTime
  (write [this]
    (-> (doto (ByteBuffer/allocate 4)
          (.put (byte 3))
          (.put (byte (.getHour this)))
          (.put (byte (.getMinute this)))
          (.put (byte (.getSecond this))))
        (.array)))

  LocalDateTime
  (write [this]
    (-> (doto (ByteBuffer/allocate 8)
          (.put (byte 4))
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
          (.put (byte 5))
          (.putShort (.getYear this))
          (.put (byte (.getMonthValue this)))
          (.put (byte (.getDayOfMonth this)))
          (.put (byte (.getHour this)))
          (.put (byte (.getMinute this)))
          (.put (byte (.getSecond this)))
          (.putShort (quot (.getTotalSeconds (.getOffset this)) 60)))
        (.array))))


(defn read [bytes]
  (let [bb (ByteBuffer/wrap bytes)]
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
                         0 (ZoneOffset/ofTotalSeconds (* (.getShort bb) 60))))))


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

  (read (write (Year/of 2012)))
  (read (write (YearMonth/of 2012 2)))
  (read (write (LocalDate/of 2012 2 29)))
  (read (write (LocalTime/of 12 13 14)))
  (read (write (LocalDateTime/of 2012 2 29 12 13 14)))
  (read (write (OffsetDateTime/of 2012 2 29 12 13 14 0 (ZoneOffset/ofHours 1))))

  )

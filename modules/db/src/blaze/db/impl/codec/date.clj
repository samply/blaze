(ns blaze.db.impl.codec.date
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :refer [number]]
   [blaze.fhir.spec.type.system :as system]))

(defn encode-lower-bound
  "Encodes the lower bound of the implicit range of `date-time` as number
  representing the seconds since epoch."
  [date-time]
  (number (system/date-time-lower-bound date-time)))

(defn encode-upper-bound
  "Encodes the upper bound of the implicit range of `date-time` as number
  representing the seconds since epoch."
  [date-time]
  (number (system/date-time-upper-bound date-time)))

(defn- encode-range* [lower-bound upper-bound]
  (-> (bb/allocate (+ 2 (bs/size lower-bound) (bs/size upper-bound)))
      (bb/put-null-terminated-byte-string! lower-bound)
      (bb/put-byte-string! upper-bound)
      (bb/put-byte! (bs/size lower-bound))
      bb/flip!
      bs/from-byte-buffer!))

(defn encode-range
  "Encodes the implicit range of `date-time` or the explicit range from `start`
  to `end`."
  ([date-time]
   (encode-range date-time date-time))
  ([start end]
   (encode-range* (encode-lower-bound start) (encode-upper-bound end))))

(defn lower-bound-bytes
  "Returns the bytes of the lower bound from the encoded `date-range-bytes`."
  [date-range-bytes]
  (let [lower-bound-size-idx (unchecked-dec-int (bs/size date-range-bytes))]
    (bs/subs date-range-bytes 0 (bs/nth date-range-bytes lower-bound-size-idx))))

(defn upper-bound-bytes
  "Returns the bytes of the upper bound from the encoded `date-range-bytes`."
  [date-range-bytes]
  (let [lower-bound-size-idx (unchecked-dec-int (bs/size date-range-bytes))
        start (unchecked-inc-int (int (bs/nth date-range-bytes lower-bound-size-idx)))]
    (bs/subs date-range-bytes start lower-bound-size-idx)))

(ns blaze.db.impl.codec.date
  (:require
   [blaze.byte-string :as bs]
   [blaze.byte-string-builder :as bsb]
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
  (-> (bsb/allocate (+ 2 (bs/size lower-bound) (bs/size upper-bound)))
      (bsb/put-null-terminated-byte-string! lower-bound)
      (bsb/put-byte-string! upper-bound)
      (bsb/put-byte! (bs/size lower-bound))
      bsb/build))

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

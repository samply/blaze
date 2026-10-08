(ns blaze.luid.impl
  (:import
   [java.nio.charset StandardCharsets]
   [java.time Clock]
   [java.util Random]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:const ^long timestamp-bits
  44)

(def ^:const ^long entropy-bits
  36)

(def ^:const ^long luid-bits
  (+ timestamp-bits entropy-bits))

(def ^:private ^:const ^long luid-half-bits
  (quot luid-bits 2))

(def ^:private ^:const ^long timestamp-low-bits
  (- timestamp-bits luid-half-bits))

(def ^:private ^:const ^long char-bits
  5)

(def ^:private ^:const ^long shift-start
  (- luid-half-bits char-bits))

(def ^:const ^long timestamp-mask
  (dec (bit-shift-left 1 timestamp-bits)))

(def ^:const ^long entropy-mask
  (dec (bit-shift-left 1 entropy-bits)))

(def ^:const ^long char-length
  (quot luid-bits char-bits))

(def ^:private ^:const ^long half-char-length
  (quot luid-half-bits char-bits))

(def ^:private ^:const ^long timestamp-low-mask
  (dec (bit-shift-left 1 timestamp-low-bits)))

(def ^:private ^:const ^long char-mask
  (dec (bit-shift-left 1 char-bits)))

(defn timestamp-overflow-ex
  "Returns the exception thrown on LUID timestamp overflow.

  An exception is used instead of an anomaly, because a timestamp overflow
  isn't an outcome callers could handle. Like the overflow in `Math/addExact`,
  it signals a broken invariant. Masking the timestamp instead would wrap it
  around to 1970, breaking both the lexicographic sortability of LUIDs and
  their collision probability."
  []
  (ArithmeticException. "LUID timestamp overflow."))

(defn timestamp
  "Returns the current millisecond of `clock`.

  Throws an ArithmeticException on timestamp overflow, i.e. if the current
  millisecond doesn't fit into the 44 bit of the timestamp component. That is
  the case for a `clock` before 1970 or after about the year 2527."
  ^long [clock]
  (let [millis (.millis ^Clock clock)]
    (if (= millis (bit-and millis timestamp-mask))
      millis
      (throw (timestamp-overflow-ex)))))

(defn next-entropy!
  "Draws a single long from `rng` and returns its lower 36 bit."
  ^long [rng]
  (bit-and (.nextLong ^Random rng) entropy-mask))

(let [alphabet (.getBytes "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" StandardCharsets/ISO_8859_1)]
  (defn luid
    "Encodes the 80 bit value consisting of the 44 bit `timestamp` followed by
    the 36 bit `entropy` as base 32 string of length 16.

    Both `timestamp` and `entropy` have to be correctly masked. Use the
    functions `timestamp` and `next-entropy!` to obtain masked values."
    [^long timestamp ^long entropy]
    ;; Because 80 bit is a multiple of 5 bit, no padding is needed. The value is
    ;; split into two 40 bit halves, each of them yielding 8 bytes.
    (let [hi (unsigned-bit-shift-right timestamp timestamp-low-bits)
          lo (bit-or (bit-shift-left (bit-and timestamp timestamp-low-mask) entropy-bits)
                     entropy)
          ;; a byte array is used instead of a char array, because the compact
          ;; Latin-1 string can be created by just copying the bytes
          bs (byte-array char-length)]
      (loop [i 0 shift shift-start]
        (when (< i half-char-length)
          (aset bs i (aget alphabet (bit-and (unsigned-bit-shift-right hi shift) char-mask)))
          (aset bs (+ half-char-length i) (aget alphabet (bit-and (unsigned-bit-shift-right lo shift) char-mask)))
          (recur (inc i) (- shift char-bits))))
      (String. bs StandardCharsets/ISO_8859_1))))

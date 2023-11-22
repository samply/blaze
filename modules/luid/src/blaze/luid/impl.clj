(ns blaze.luid.impl
  (:import
   [com.google.common.io BaseEncoding]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn luid [^long timestamp ^long entropy]
  (let [high (BigInteger/valueOf (bit-and timestamp 0xFFFFFFFFFFF))
        low (BigInteger/valueOf entropy)
        bs (-> (.add (.shiftLeft high 36) low)
               (.add (.shiftLeft BigInteger/ONE 80))
               (.toByteArray))]
    (.encode (BaseEncoding/base32) bs 1 10)))

(ns blaze.page-store.local.token
  (:import
    [com.google.common.io BaseEncoding]
    [java.util Random]))


(set! *warn-on-reflection* true)


(defn generate [secure-rng]
  (let [bs (byte-array 20)]
    (.nextBytes ^Random secure-rng bs)
    (.encode (BaseEncoding/base32) bs)))

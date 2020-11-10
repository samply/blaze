(ns blaze.db.impl.codec-test-perf
  (:require
    [blaze.db.impl.codec :as codec]
    [clojure.test :refer [deftest is testing]]
    [criterium.core :as criterium])
  (:import
    [com.google.protobuf ByteString]))


(deftest sp-value-resource-key-test
  (let [value (ByteString/copyFrom (byte-array 16))]
    (criterium/quick-bench (codec/sp-value-resource-key 0 0 value))))

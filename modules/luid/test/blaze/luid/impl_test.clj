(ns blaze.luid.impl-test
  (:refer-clojure :exclude [str])
  (:require
   [blaze.luid.impl :as impl]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [blaze.util :refer [str]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [java-time.api :as time])
  (:import
   [com.google.common.io BaseEncoding]
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest timestamp-test
  (testing "valid timestamps"
    (doseq [millis [0 1 impl/timestamp-mask]]
      (is (= millis (impl/timestamp (time/fixed-clock (time/instant millis) "UTC"))))))

  (testing "timestamp overflow"
    (doseq [millis [-1 (inc impl/timestamp-mask) Long/MIN_VALUE Long/MAX_VALUE]]
      (is (thrown-with-msg? ArithmeticException #"LUID timestamp overflow\."
                            (impl/timestamp (time/fixed-clock (time/instant millis) "UTC")))))))

(def millis-2020
  (.toEpochMilli (Instant/parse "2020-01-01T00:00:00Z")))

(deftest internal-luid-test
  (testing "maximum time"
    (testing "maximum entropy"
      (is (= (apply str (repeat impl/char-length \7))
             (impl/luid impl/timestamp-mask impl/entropy-mask))))

    (testing "zero entropy"
      (is (= "777777776AAAAAAA"
             (impl/luid impl/timestamp-mask 0)))))

  (testing "zero time"
    (testing "maximum entropy"
      (is (= "AAAAAAAAB7777777"
             (impl/luid 0 impl/entropy-mask))))

    (testing "zero entropy"
      (is (= (apply str (repeat impl/char-length \A))
             (impl/luid 0 0)))))

  (testing "start of 2020"
    (testing "half entropy"
      (is (= "C326M3UAB3777777"
             (impl/luid millis-2020 0xEFFFFFFFF))))))

(defn- reference-luid
  "The former implementation based on `BigInteger` and Guava's `BaseEncoding`
  used as reference."
  [^long timestamp ^long entropy]
  (let [high (BigInteger/valueOf timestamp)
        low (BigInteger/valueOf entropy)
        bs (-> (.add (.shiftLeft high impl/entropy-bits) low)
               (.add (.shiftLeft BigInteger/ONE impl/luid-bits))
               (.toByteArray))]
    (.encode (BaseEncoding/base32) bs 1 10)))

(defn- with-edge-cases
  "Returns a generator of longs between 0 and `mask` (inclusive) which also
  generates `edge-cases` frequently."
  [mask edge-cases]
  (gen/frequency [[1 (gen/elements edge-cases)] [3 (gen/choose 0 mask)]]))

(def ^:private timestamp-gen
  ;; 0xF and 0x10 are at the border between the two 40 bit halves
  (with-edge-cases impl/timestamp-mask [0 0xF 0x10 impl/timestamp-mask]))

(def ^:private entropy-gen
  (with-edge-cases impl/entropy-mask [0 impl/entropy-mask]))

(deftest luid-equals-reference-test
  (satisfies-prop 100000
    (prop/for-all [timestamp timestamp-gen
                   entropy entropy-gen]
      (= (reference-luid timestamp entropy) (impl/luid timestamp entropy)))))

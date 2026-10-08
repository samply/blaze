(ns blaze.db.impl.codec-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defmacro check
  ([sym]
   `(is (not-every? :failure (st/check ~sym))))
  ([sym opts]
   `(is (not-every? :failure (st/check ~sym ~opts)))))

(deftest id-string-id-byte-string-test
  (satisfies-prop 1000
    (prop/for-all [s (s/gen :blaze.resource/id)]
      (= s
         (codec/id-string (codec/id-byte-string s))
         (apply codec/id-string [(apply codec/id-byte-string [s])])))))

(deftest descending-long-test
  (are [t dt] (= dt (codec/descending-long t))
    1 0xFFFFFFFFFFFFFE
    0 0xFFFFFFFFFFFFFF)

  (satisfies-prop 100000
    (prop/for-all [t gen/nat]
      (= t
         (codec/descending-long (codec/descending-long t))
         (apply codec/descending-long [(apply codec/descending-long [t])])))))

(deftest tid-test
  (check `codec/tid))

(deftest all-types-test
  (testing "contains types without own search parameters"
    (is (every? (set codec/all-types) ["Binary" "Parameters" "OperationOutcome"])))

  (testing "every type can be converted to a tid and back"
    (is (every? #(= % (codec/tid->type (codec/tid %))) codec/all-types))))

(deftest string-test
  (satisfies-prop 100
    (prop/for-all [s (s/gen string?)]
      (= s
         (bs/to-string-utf8 (codec/string s))
         (bs/to-string-utf8 (apply codec/string [s]))))))

(deftest number-test
  (testing "encode/decode"
    (satisfies-prop 10000
      (prop/for-all [i (s/gen int?)]
        (= i (codec/decode-number (codec/number i))))))

  (testing "Long"
    (are [n bs] (= bs (codec/number n))
      Long/MIN_VALUE #blaze/byte-string"3F8000000000000000"
      (inc Long/MIN_VALUE) #blaze/byte-string"3F8000000000000001"
      -576460752303423489 #blaze/byte-string"3FF7FFFFFFFFFFFFFF"
      -576460752303423488 #blaze/byte-string"4000000000000000"
      -576460752303423487 #blaze/byte-string"4000000000000001"
      -2251799813685249 #blaze/byte-string"47F7FFFFFFFFFFFF"
      -2251799813685248 #blaze/byte-string"48000000000000"
      -2251799813685247 #blaze/byte-string"48000000000001"
      -8796093022209 #blaze/byte-string"4FF7FFFFFFFFFF"
      -8796093022208 #blaze/byte-string"500000000000"
      -8796093022207 #blaze/byte-string"500000000001"
      -34359738369 #blaze/byte-string"57F7FFFFFFFF"
      -34359738368 #blaze/byte-string"5800000000"
      -34359738367 #blaze/byte-string"5800000001"
      -134217729 #blaze/byte-string"5FF7FFFFFF"
      -134217728 #blaze/byte-string"60000000"
      -134217727 #blaze/byte-string"60000001"
      -524289 #blaze/byte-string"67F7FFFF"
      -524288 #blaze/byte-string"680000"
      -524287 #blaze/byte-string"680001"
      -2050 #blaze/byte-string"6FF7FE"
      -2049 #blaze/byte-string"6FF7FF"
      -2048 #blaze/byte-string"7000"
      -10 #blaze/byte-string"77F6"
      -9 #blaze/byte-string"77F7"
      -8 #blaze/byte-string"78"
      -2 #blaze/byte-string"7E"
      -1 #blaze/byte-string"7F"
      0 #blaze/byte-string"80"
      1 #blaze/byte-string"81"
      2 #blaze/byte-string"82"
      7 #blaze/byte-string"87"
      8 #blaze/byte-string"8808"
      9 #blaze/byte-string"8809"
      2047 #blaze/byte-string"8FFF"
      2048 #blaze/byte-string"900800"
      2049 #blaze/byte-string"900801"
      524287 #blaze/byte-string"97FFFF"
      524288 #blaze/byte-string"98080000"
      524289 #blaze/byte-string"98080001"
      134217727 #blaze/byte-string"9FFFFFFF"
      134217728 #blaze/byte-string"A008000000"
      134217729 #blaze/byte-string"A008000001"
      34359738367 #blaze/byte-string"A7FFFFFFFF"
      34359738368 #blaze/byte-string"A80800000000"
      34359738369 #blaze/byte-string"A80800000001"
      8796093022207 #blaze/byte-string"AFFFFFFFFFFF"
      8796093022208 #blaze/byte-string"B0080000000000"
      8796093022209 #blaze/byte-string"B0080000000001"
      2251799813685247 #blaze/byte-string"B7FFFFFFFFFFFF"
      2251799813685248 #blaze/byte-string"B808000000000000"
      2251799813685249 #blaze/byte-string"B808000000000001"
      576460752303423487 #blaze/byte-string"BFFFFFFFFFFFFFFF"
      576460752303423488 #blaze/byte-string"C00800000000000000"
      576460752303423489 #blaze/byte-string"C00800000000000001"
      Long/MAX_VALUE #blaze/byte-string"C07FFFFFFFFFFFFFFF"))

  (testing "Integer"
    (are [n bs] (= bs (codec/number n))
      Integer/MIN_VALUE #blaze/byte-string"5F80000000"
      (int -1) #blaze/byte-string"7F"
      (int 0) #blaze/byte-string"80"
      (int 1) #blaze/byte-string"81"
      Integer/MAX_VALUE #blaze/byte-string"A07FFFFFFF")))

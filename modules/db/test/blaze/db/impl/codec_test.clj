(ns blaze.db.impl.codec-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.test-util :refer [satisfies-prop]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop])
  (:import
    [java.nio.charset StandardCharsets]
    [java.time OffsetDateTime ZoneOffset]))


(set! *warn-on-reflection* true)
(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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


(deftest string-test
  (satisfies-prop 100
    (prop/for-all [s (s/gen string?)]
      (= s
         (bs/to-string (codec/string s) StandardCharsets/UTF_8)
         (bs/to-string (apply codec/string [s]) StandardCharsets/UTF_8)))))


(def zo
  (ZoneOffset/ofHours 0))


(deftest date-lb-test
  (testing "year"
    (are [date hex] (= hex (bs/hex (codec/date-lb zo date)))
      #system/date"1970" "80"
      #system/date-time"1970" "80"))

  (testing "year-month"
    (are [date hex] (= hex (bs/hex (codec/date-lb zo date)))
      #system/date"1970-01" "80"
      #system/date-time"1970-01" "80"))

  (testing "local-date"
    (are [date hex] (= hex (bs/hex (codec/date-lb zo date)))
      #system/date"1970-01-01" "80"
      #system/date-time"1970-01-01" "80"))

  (testing "local-date-time"
    (are [date hex] (= hex (bs/hex (codec/date-lb zo date)))
      #system/date-time"1970-01-01T00:00" "80"))

  (testing "offset-date-time"
    (are [date hex] (= hex (bs/hex (codec/date-lb zo date)))
      (OffsetDateTime/of 1970 1 1 0 0 0 0 ZoneOffset/UTC) "80"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours 2)) "6FE3E0"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours 1)) "6FF1F0"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours -1)) "900E10"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours -2)) "901C20")))


(deftest date-ub-test
  (testing "year"
    (are [date hex] (= hex (bs/hex (codec/date-ub zo date)))
      #system/date"1969" "7F"
      #system/date-time"1969" "7F"))

  (testing "year-month"
    (are [date hex] (= hex (bs/hex (codec/date-ub zo date)))
      #system/date"1969-12" "7F"
      #system/date-time"1969-12" "7F"))

  (testing "local-date"
    (are [date hex] (= hex (bs/hex (codec/date-ub zo date)))
      #system/date"1969-12-31" "7F"
      #system/date-time"1969-12-31" "7F"))

  (testing "local-date-time"
    (are [date hex] (= hex (bs/hex (codec/date-ub zo date)))
      #system/date-time"1969-12-31T23:59:59" "7F"))

  (testing "offset-date-time"
    (are [date hex] (= hex (bs/hex (codec/date-ub zo date)))
      (OffsetDateTime/of 1969 12 31 23 59 59 0 ZoneOffset/UTC) "7F"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours 2)) "6FE3DF"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours 1)) "6FF1EF"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours -1)) "900E0F"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours -2)) "901C1F")))


(deftest date-lb-ub-test
  (testing "extract lower bound"
    (is (=
          (codec/date-lb-ub->lb
            (codec/date-lb-ub (codec/date-lb zo #system/date"2020") (codec/date-ub zo #system/date"2020")))
          (codec/date-lb zo #system/date"2020"))))

  (testing "extract upper bound"
    (is (=
          (codec/date-lb-ub->ub
            (codec/date-lb-ub (codec/date-lb zo #system/date"2020") (codec/date-ub zo #system/date"2020")))
          (codec/date-ub zo #system/date"2020")))))


(deftest number-test
  (testing "long"
    (are [n hex] (= hex (bs/hex (codec/number n)))
      Long/MIN_VALUE "3F8000000000000000"
      (inc Long/MIN_VALUE) "3F8000000000000001"
      -576460752303423489 "3FF7FFFFFFFFFFFFFF"
      -576460752303423488 "4000000000000000"
      -576460752303423487 "4000000000000001"
      -2251799813685249 "47F7FFFFFFFFFFFF"
      -2251799813685248 "48000000000000"
      -2251799813685247 "48000000000001"
      -8796093022209 "4FF7FFFFFFFFFF"
      -8796093022208 "500000000000"
      -8796093022207 "500000000001"
      -34359738369 "57F7FFFFFFFF"
      -34359738368 "5800000000"
      -34359738367 "5800000001"
      -134217729 "5FF7FFFFFF"
      -134217728 "60000000"
      -134217727 "60000001"
      -524289 "67F7FFFF"
      -524288 "680000"
      -524287 "680001"
      -2050 "6FF7FE"
      -2049 "6FF7FF"
      -2048 "7000"
      -10 "77F6"
      -9 "77F7"
      -8 "78"
      -2 "7E"
      -1 "7F"
      0 "80"
      1 "81"
      2 "82"
      7 "87"
      8 "8808"
      9 "8809"
      2047 "8FFF"
      2048 "900800"
      2049 "900801"
      524287 "97FFFF"
      524288 "98080000"
      524289 "98080001"
      134217727 "9FFFFFFF"
      134217728 "A008000000"
      134217729 "A008000001"
      34359738367 "A7FFFFFFFF"
      34359738368 "A80800000000"
      34359738369 "A80800000001"
      8796093022207 "AFFFFFFFFFFF"
      8796093022208 "B0080000000000"
      8796093022209 "B0080000000001"
      2251799813685247 "B7FFFFFFFFFFFF"
      2251799813685248 "B808000000000000"
      2251799813685249 "B808000000000001"
      576460752303423487 "BFFFFFFFFFFFFFFF"
      576460752303423488 "C00800000000000000"
      576460752303423489 "C00800000000000001"
      Long/MAX_VALUE "C07FFFFFFFFFFFFFFF"))

  (testing "integer"
    (are [n hex] (= hex (bs/hex (codec/number n)))
      Integer/MIN_VALUE "5F80000000"
      (int -1) "7F"
      (int 0) "80"
      (int 1) "81"
      Integer/MAX_VALUE "A07FFFFFFF")))

(ns blaze.db.impl.codec.date-test
  (:require
   [blaze.byte-string :as bs]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.codec.date :as codec-date]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.fhir.spec.generators :as fg]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.properties :as prop])
  (:import
   [java.time OffsetDateTime ZoneOffset]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest encode-lower-bound-test
  (testing "year"
    (are [date hex] (= hex (bs/hex (codec-date/encode-lower-bound date)))
      #system/date"1970" "80"
      #system/date-time"1970" "80"))

  (testing "year-month"
    (are [date hex] (= hex (bs/hex (codec-date/encode-lower-bound date)))
      #system/date"1970-01" "80"
      #system/date-time"1970-01" "80"))

  (testing "local-date"
    (are [date hex] (= hex (bs/hex (codec-date/encode-lower-bound date)))
      #system/date"1970-01-01" "80"
      #system/date-time"1970-01-01" "80"))

  (testing "local-date-time"
    (are [date hex] (= hex (bs/hex (codec-date/encode-lower-bound date)))
      #system/date-time"1970-01-01T00:00" "80"))

  (testing "offset-date-time"
    (are [date hex] (= hex (bs/hex (codec-date/encode-lower-bound date)))
      (OffsetDateTime/of 1970 1 1 0 0 0 0 ZoneOffset/UTC) "80"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours 2)) "6FE3E0"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours 1)) "6FF1F0"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours -1)) "900E10"
      (OffsetDateTime/of 1970 1 1 0 0 0 0 (ZoneOffset/ofHours -2)) "901C20"))

  (testing "nil"
    (is (= (codec-date/encode-lower-bound #system/date"0001")
           (codec-date/encode-lower-bound nil)))))

(deftest encode-upper-bound-test
  (testing "year"
    (are [date hex] (= hex (bs/hex (codec-date/encode-upper-bound date)))
      #system/date"1969" "7F"
      #system/date-time"1969" "7F"))

  (testing "year-month"
    (are [date hex] (= hex (bs/hex (codec-date/encode-upper-bound date)))
      #system/date"1969-12" "7F"
      #system/date-time"1969-12" "7F"))

  (testing "local-date"
    (are [date hex] (= hex (bs/hex (codec-date/encode-upper-bound date)))
      #system/date"1969-12-31" "7F"
      #system/date-time"1969-12-31" "7F"))

  (testing "local-date-time"
    (are [date hex] (= hex (bs/hex (codec-date/encode-upper-bound date)))
      #system/date-time"1969-12-31T23:59:59" "7F"))

  (testing "offset-date-time"
    (are [date hex] (= hex (bs/hex (codec-date/encode-upper-bound date)))
      (OffsetDateTime/of 1969 12 31 23 59 59 0 ZoneOffset/UTC) "7F"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours 2)) "6FE3DF"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours 1)) "6FF1EF"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours -1)) "900E0F"
      (OffsetDateTime/of 1969 12 31 23 59 59 0 (ZoneOffset/ofHours -2)) "901C1F"))

  (testing "nil"
    (is (= (codec-date/encode-upper-bound #system/date"9999")
           (codec-date/encode-upper-bound nil)))))

(deftest encode-range-test
  (testing "extract lower bound"
    (satisfies-prop 100
      (prop/for-all [date fg/date-value]
        (= (codec-date/lower-bound-bytes (codec-date/encode-range date))
           (codec-date/encode-lower-bound date)))))

  (testing "extract upper bound"
    (satisfies-prop 100
      (prop/for-all [date fg/date-value]
        (= (codec-date/upper-bound-bytes (codec-date/encode-range date))
           (codec-date/encode-upper-bound date))))))

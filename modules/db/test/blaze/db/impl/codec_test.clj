(ns blaze.db.impl.codec-test
  (:require
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.codec-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [clojure.test.check]
    [clojure.test.check.generators :as gen])
  (:import
    [java.time ZoneOffset])
  (:refer-clojure :exclude [hash]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn byte-array-gen [size]
  #(gen/fmap byte-array (gen/vector gen/byte size)))


(defmacro check
  ([sym]
   `(is (not-every? :failure (st/check ~sym))))
  ([sym opts]
   `(is (not-every? :failure (st/check ~sym ~opts)))))



;; ---- ResourceValue Index ---------------------------------------------------



;; ---- ResourceType Index ----------------------------------------------------

(deftest resource-type-key
  (check `codec/resource-type-key))



;; ---- CompartmentResourceType Index -----------------------------------------

(deftest compartment-resource-type-key
  (check `codec/compartment-resource-type-key))



;; ---- ResourceAsOf Index ----------------------------------------------------

(deftest resource-as-of-key
  (check `codec/resource-as-of-key))



;; ---- ResourceAsOf Index ----------------------------------------------------

(deftest resource-as-of-value
  (check
    `codec/resource-as-of-value
    {:gen {:blaze.resource/hash (byte-array-gen codec/hash-size)}}))



;; ---- TypeAsOf Index --------------------------------------------------------

(deftest type-as-of-key
  (check `codec/type-as-of-key))


(deftest tid
  (check `codec/tid))


(def zo
  (ZoneOffset/ofHours 0))


(deftest date-lb
  (testing "year"
    (are [date hex] (= hex (codec/hex (codec/date-lb zo date)))
      "1970" "80"))

  (testing "year-month"
    (are [date hex] (= hex (codec/hex (codec/date-lb zo date)))
      "1970-01" "80"))

  (testing "date"
    (are [date hex] (= hex (codec/hex (codec/date-lb zo date)))
      "1970-01-01" "80"))

  (testing "date-time"
    (are [date hex] (= hex (codec/hex (codec/date-lb zo date)))
      "1970-01-01T00:00:00" "80"))

  (testing "date-time with milliseconds"
    (are [date hex] (= hex (codec/hex (codec/date-lb zo date)))
      "1970-01-01T00:00:00.000" "80"))

  (testing "date-time with timezone"
    (are [date hex] (= hex (codec/hex (codec/date-lb zo date)))
      "1970-01-01T00:00:00Z" "80"
      "1970-01-01T00:00:00+00:00" "80"
      "1970-01-01T00:00:00+02:00" "6FE3E0"
      "1970-01-01T00:00:00+01:00" "6FF1F0"
      "1970-01-01T00:00:00-01:00" "900E10"
      "1970-01-01T00:00:00-02:00" "901C20"))

  (testing "date-time with milliseconds and timezone"
    (are [date hex] (= hex (codec/hex (codec/date-lb zo date)))
      "1970-01-01T00:00:00.000Z" "80"
      "1970-01-01T00:00:00.000+00:00" "80"
      "1970-01-01T00:00:00.000+02:00" "6FE3E0"
      "1970-01-01T00:00:00.000+01:00" "6FF1F0"
      "1970-01-01T00:00:00.000-01:00" "900E10"
      "1970-01-01T00:00:00.000-02:00" "901C20")))


(deftest date-ub
  (testing "year"
    (are [date hex] (= hex (codec/hex (codec/date-ub zo date)))
      "1969" "B00EFFFFFFFFFF"))

  (testing "year-month"
    (are [date hex] (= hex (codec/hex (codec/date-ub zo date)))
      "1969-12" "B00EFFFFFFFFFF"))

  (testing "date"
    (are [date hex] (= hex (codec/hex (codec/date-ub zo date)))
      "1969-12-31" "B00EFFFFFFFFFF"))

  (testing "date-time"
    (are [date hex] (= hex (codec/hex (codec/date-ub zo date)))
      "1969-12-31T23:59:59" "B00EFFFFFFFFFF"))

  (testing "date-time with milliseconds"
    (are [date hex] (= hex (codec/hex (codec/date-ub zo date)))
      "1969-12-31T23:59:59.000" "B00EFFFFFFFFFF"))

  (testing "date-time with timezone"
    (are [date hex] (= hex (codec/hex (codec/date-ub zo date)))
      "1969-12-31T23:59:59Z" "B00EFFFFFFFFFF"
      "1969-12-31T23:59:59+00:00" "B00EFFFFFFFFFF"
      "1969-12-31T23:59:59+02:00" "B00EFFFFFFE3DF"
      "1969-12-31T23:59:59+01:00" "B00EFFFFFFF1EF"
      "1969-12-31T23:59:59-01:00" "B00F0000000E0F"
      "1969-12-31T23:59:59-02:00" "B00F0000001C1F"))

  (testing "date-time with milliseconds and timezone"
    (are [date hex] (= hex (codec/hex (codec/date-ub zo date)))
      "1969-12-31T23:59:59.000Z" "B00EFFFFFFFFFF"
      "1969-12-31T23:59:59.000+00:00" "B00EFFFFFFFFFF"
      "1969-12-31T23:59:59.000+02:00" "B00EFFFFFFE3DF"
      "1969-12-31T23:59:59.000+01:00" "B00EFFFFFFF1EF"
      "1969-12-31T23:59:59.000-01:00" "B00F0000000E0F"
      "1969-12-31T23:59:59.000-02:00" "B00F0000001C1F")))


(deftest date
  (testing "upper bounds are always bigger than lower bounds"
    (is (bytes/< (codec/date-lb zo "9999") (codec/date-ub zo "0001")))))


(deftest date-lb-ub
  (testing "extract lower bound"
    (is (bytes/=
          (codec/date-lb-ub->lb
            (codec/date-lb-ub (codec/date-lb zo "2020") (codec/date-ub zo "2020")))
          (codec/date-lb zo "2020"))))

  (testing "extract upper bound"
    (is (bytes/=
          (codec/date-lb-ub->ub
            (codec/date-lb-ub (codec/date-lb zo "2020") (codec/date-ub zo "2020")))
          (codec/date-ub zo "2020")))))


(deftest date-lb?
  (is (codec/date-lb? (codec/date-lb zo "9999") 0))
  (is (not (codec/date-lb? (codec/date-ub zo "0001") 0))))


(deftest date-ub?
  (is (codec/date-ub? (codec/date-ub zo "0001") 0))
  (is (not (codec/date-ub? (codec/date-lb zo "9999") 0))))


(deftest number
  (testing "long"
    (are [n hex] (= hex (codec/hex (codec/number n)))
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
      Long/MAX_VALUE "C07FFFFFFFFFFFFFFF")))

(deftest hash
  (testing "bit length is 256"
    (is (= 32 (count (vec (codec/hash {:resourceType "Patient" :id "0"})))))))

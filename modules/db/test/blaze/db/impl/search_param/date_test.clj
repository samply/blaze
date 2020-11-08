(ns blaze.db.impl.search-param.date-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec.type :as type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time LocalDate OffsetDateTime ZoneId ZoneOffset]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def birth-date-param
  (sr/get search-param-registry "birthdate" "Patient"))


(deftest code-test
  (is (= "birthdate" (:code birth-date-param))))


(deftest name-test
  (is (= "birthdate" (:name birth-date-param))))


(deftest compile-value-test
  (testing "invalid date value"
    (given (search-param/compile-values birth-date-param ["a"])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid date-time value `a` in search parameter `birthdate`."))

  (testing "unsupported prefix"
    (given (search-param/compile-values birth-date-param ["ne2020"])
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported prefix `ne` in search parameter `birthdate`.")))


(deftest index-entries-test
  (testing "Patient birthDate"
    (let [patient {:fhir/type :fhir/Patient
                   :id "id-142629"
                   :birthDate #fhir/date"2020-02-04"}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries birth-date-param hash patient [])]

      (testing "the first entry is about the lower bound of `2020-02-04`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "birthdate")
                (codec/tid "Patient")
                (codec/date-lb (ZoneId/systemDefault) (LocalDate/of 2020 2 4))
                (codec/id-bytes "id-142629")
                hash))))

      (testing "the second entry is about the upper bound of `2020-02-04`"
        (is (bytes/=
              k1
              (codec/sp-value-resource-key
                (codec/c-hash "birthdate")
                (codec/tid "Patient")
                (codec/date-ub (ZoneId/systemDefault) (LocalDate/of 2020 2 4))
                (codec/id-bytes "id-142629")
                hash))))))

  (testing "Encounter date"
    (let [patient {:fhir/type :fhir/Encounter
                   :id "id-160224"
                   :period
                   {:fhir/type :fhir/Period
                    :start #fhir/dateTime"2019-11-17T00:14:29+01:00"
                    :end #fhir/dateTime"2019-11-17T00:44:29+01:00"}}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "date" "Encounter")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29+01:00`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-lb
                  (ZoneId/systemDefault)
                  (OffsetDateTime/of 2019 11 17 0 14 29 0 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-160224")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:44:29+01:00`"
        (is (bytes/=
              k1
              (codec/sp-value-resource-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-ub
                  (ZoneId/systemDefault)
                  (OffsetDateTime/of 2019 11 17 0 44 29 0 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-160224")
                hash))))))

  (testing "Encounter date without start"
    (let [patient {:fhir/type :fhir/Encounter
                   :id "id-160224"
                   :period
                   {:fhir/type :fhir/Period
                    :end #fhir/dateTime"2019-11-17"}}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "date" "Encounter")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29+01:00`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                codec/date-min-bound
                (codec/id-bytes "id-160224")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17`"
        (is (bytes/=
              k1
              (codec/sp-value-resource-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-ub (ZoneId/systemDefault) (LocalDate/of 2019 11 17))
                (codec/id-bytes "id-160224")
                hash))))))

  (testing "Encounter date without end"
    (let [patient {:fhir/type :fhir/Encounter
                   :id "id-160224"
                   :period
                   {:fhir/type :fhir/Period
                    :start #fhir/dateTime"2019-11-17T00:14:29+01:00"}}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "date" "Encounter")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29+01:00`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-lb
                  (ZoneId/systemDefault)
                  (OffsetDateTime/of 2019 11 17 0 14 29 0 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-160224")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:44:29+01:00`"
        (is (bytes/=
              k1
              (codec/sp-value-resource-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                codec/date-max-bound
                (codec/id-bytes "id-160224")
                hash))))))

  (testing "DiagnosticReport issued"
    (let [patient {:fhir/type :fhir/DiagnosticReport
                   :id "id-155607"
                   :issued (type/->Instant "2019-11-17T00:14:29.917+01:00")}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "issued" "DiagnosticReport")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29.917+01:00`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "issued")
                (codec/tid "DiagnosticReport")
                (codec/date-lb
                  (ZoneId/systemDefault)
                  (OffsetDateTime/of 2019 11 17 0 14 29 917 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-155607")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:14:29.917+01:00`"
        (is (bytes/=
              k1
              (codec/sp-value-resource-key
                (codec/c-hash "issued")
                (codec/tid "DiagnosticReport")
                (codec/date-ub
                  (ZoneId/systemDefault)
                  (OffsetDateTime/of 2019 11 17 0 14 29 917 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-155607")
                hash)))))))

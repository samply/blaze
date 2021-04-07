(ns blaze.db.impl.search-param.date-test
  (:require
    [blaze.byte-string-spec]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.impl.search-param.date :as spd]
    [blaze.db.impl.search-param.date-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type :as type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time LocalDate OffsetDateTime ZoneId ZoneOffset]))


(st/instrument)
(log/set-level! :trace)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def birth-date-param
  (sr/get search-param-registry "birthdate" "Patient"))


(deftest name-test
  (is (= "birthdate" (:name birth-date-param))))


(deftest code-test
  (is (= "birthdate" (:code birth-date-param))))


(deftest c-hash-test
  (is (= (codec/c-hash "birthdate") (:c-hash birth-date-param))))


(deftest compile-value-test
  (testing "invalid date value"
    (given (search-param/compile-values birth-date-param nil ["a"])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid date-time value `a` in search parameter `birthdate`."))

  (testing "unsupported prefix"
    (given (search-param/compile-values birth-date-param nil ["ne2020"])
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported prefix `ne` in search parameter `birthdate`.")))


(defn- date-lb [date-time]
  (codec/date-lb (ZoneId/systemDefault) date-time))


(defn- date-ub [date-time]
  (codec/date-ub (ZoneId/systemDefault) date-time))


(deftest index-entries-test
  (testing "Patient"
    (testing "birthDate"
      (let [patient {:fhir/type :fhir/Patient
                     :id "id-142629"
                     :birthDate #fhir/date"2020-02-04"}
            hash (hash/generate patient)
            [[_ k0]]
            (search-param/index-entries birth-date-param [] hash patient)]

        (testing "the entry is about both bounds of `2020-02-04`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "birthdate"
            :type := "Patient"
            :v-hash := (codec/date-lb-ub
                         (date-lb (LocalDate/of 2020 2 4))
                         (date-ub (LocalDate/of 2020 2 4)))
            :id := "id-142629"
            :hash-prefix (codec/hash-prefix hash)))))

    (testing "death-date"
      (let [patient
            {:fhir/type :fhir/Patient
             :id "id-142629"
             :deceased #fhir/dateTime"2019-11-17T00:14:29+01:00"}
            hash (hash/generate patient)
            [[_ k0]]
            (search-param/index-entries
              (sr/get search-param-registry "death-date" "Patient")
              [] hash patient)]

        (testing "the entry is about both bounds of `2020-01-01T00:00:00Z`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "death-date"
            :type := "Patient"
            :v-hash := (codec/date-lb-ub
                         (date-lb
                           (OffsetDateTime/of 2019 11 17 0 14 29 0
                                              (ZoneOffset/ofHours 1)))
                         (date-ub
                           (OffsetDateTime/of 2019 11 17 0 14 29 0
                                              (ZoneOffset/ofHours 1))))
            :id := "id-142629"
            :hash-prefix (codec/hash-prefix hash))))))

  (testing "Encounter"
    (testing "date"
      (let [encounter
            {:fhir/type :fhir/Encounter :id "id-160224"
             :period
             #fhir/Period
                 {:start #fhir/dateTime"2019-11-17T00:14:29+01:00"
                  :end #fhir/dateTime"2019-11-17T00:44:29+01:00"}}
            hash (hash/generate encounter)
            [[_ k0]]
            (search-param/index-entries
              (sr/get search-param-registry "date" "Encounter")
              [] hash encounter)]

        (testing "the entry is about the lower bound of the start and the upper
                  bound of the end of the period"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "date"
            :type := "Encounter"
            :v-hash := (codec/date-lb-ub
                         (date-lb
                           (OffsetDateTime/of 2019 11 17 0 14 29 0
                                              (ZoneOffset/ofHours 1)))
                         (date-ub
                           (OffsetDateTime/of 2019 11 17 0 44 29 0
                                              (ZoneOffset/ofHours 1))))
            :id := "id-160224"
            :hash-prefix (codec/hash-prefix hash))))

      (testing "without start"
        (let [encounter
              {:fhir/type :fhir/Encounter :id "id-160224"
               :period
               #fhir/Period
                   {:end #fhir/dateTime"2019-11-17"}}
              hash (hash/generate encounter)
              [[_ k0]]
              (search-param/index-entries
                (sr/get search-param-registry "date" "Encounter")
                [] hash encounter)]

          (testing "the entry is about the min bound as lower bound and the
                    upper bound of the end of the period"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "date"
              :type := "Encounter"
              :v-hash := (codec/date-lb-ub
                           codec/date-min-bound
                           (date-ub (LocalDate/of 2019 11 17)))
              :id := "id-160224"
              :hash-prefix (codec/hash-prefix hash)))))

      (testing "Encounter date without end"
        (let [encounter
              {:fhir/type :fhir/Encounter :id "id-160224"
               :period
               #fhir/Period
                   {:start #fhir/dateTime"2019-11-17T00:14:29+01:00"}}
              hash (hash/generate encounter)
              [[_ k0]]
              (search-param/index-entries
                (sr/get search-param-registry "date" "Encounter")
                [] hash encounter)]

          (testing "the entry is about the lower bound of the start and the max
                    upper bound"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "date"
              :type := "Encounter"
              :v-hash := (codec/date-lb-ub
                           (date-lb
                             (OffsetDateTime/of 2019 11 17 0 14 29 0
                                                (ZoneOffset/ofHours 1)))
                           codec/date-max-bound)
              :id := "id-160224"
              :hash-prefix (codec/hash-prefix hash)))))))

  (testing "DiagnosticReport"
    (testing "issued"
      (let [patient {:fhir/type :fhir/DiagnosticReport
                     :id "id-155607"
                     :issued (type/->Instant "2019-11-17T00:14:29.917+01:00")}
            hash (hash/generate patient)
            [[_ k0]]
            (search-param/index-entries
              (sr/get search-param-registry "issued" "DiagnosticReport")
              [] hash patient)]

        (testing "the entry is about both bounds of `2019-11-17T00:14:29.917+01:00`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "issued"
            :type := "DiagnosticReport"
            :v-hash := (codec/date-lb-ub
                         (date-lb
                           (OffsetDateTime/of 2019 11 17 0 14 29 917
                                              (ZoneOffset/ofHours 1)))
                         (date-ub
                           (OffsetDateTime/of 2019 11 17 0 14 29 917
                                              (ZoneOffset/ofHours 1))))
            :id := "id-155607"
            :hash-prefix (codec/hash-prefix hash))))))

  (testing "FHIRPath evaluation problem"
    (let [resource {:fhir/type :fhir/DiagnosticReport :id "foo"}
          hash (hash/generate resource)]

      (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
        (given (search-param/index-entries
                 (sr/get search-param-registry "issued" "DiagnosticReport")
                 [] hash resource)
          ::anom/category := ::anom/fault))))

  (testing "skip warning"
    (is (nil? (spd/index-entries "" nil)))))

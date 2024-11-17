(ns blaze.db.impl.search-param.date-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec.date :as codec-date]
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
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn birth-date-param [search-param-registry]
  (sr/get search-param-registry "birthdate" "Patient"))

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest birth-date-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (birth-date-param search-param-registry)
      :name := "birthdate"
      :code := "birthdate"
      :c-hash := (codec/c-hash "birthdate"))))

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "invalid date value"
      (given (search-param/compile-values
              (birth-date-param search-param-registry) nil ["a"])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid date-time value `a` in search parameter `birthdate`."))

    (testing "less than"
      (given (search-param/compile-values
              (birth-date-param search-param-registry) nil ["lt2020"])
        [0 :op] := :lt
        [0 :lower-bound] := (codec-date/encode-lower-bound #system/date"2020")))))

(defn- lower-bound-instant [date-range-bytes]
  (-> date-range-bytes
      codec-date/lower-bound-bytes
      codec/decode-number
      Instant/ofEpochSecond))

(defn- upper-bound-instant [date-range-bytes]
  (-> date-range-bytes
      codec-date/upper-bound-bytes
      codec/decode-number
      Instant/ofEpochSecond))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Patient"
      (testing "birthDate"
        (let [patient {:fhir/type :fhir/Patient
                       :id "id-142629"
                       :birthDate #fhir/date"2020-02-04"}
              hash (hash/generate patient)
              [[_ k0]]
              (index-entries
               (birth-date-param search-param-registry) [] hash patient)]

          (testing "the entry is about both bounds of `2020-02-04`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "birthdate"
              :type := "Patient"
              [:v-hash lower-bound-instant] := (Instant/parse "2020-02-04T00:00:00Z")
              [:v-hash upper-bound-instant] := (Instant/parse "2020-02-04T23:59:59Z")
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)))))

      (testing "death-date"
        (let [patient
              {:fhir/type :fhir/Patient
               :id "id-142629"
               :deceased #fhir/dateTime"2019-11-17T00:14:29+01:00"}
              hash (hash/generate patient)
              [[_ k0]]
              (index-entries
               (sr/get search-param-registry "death-date" "Patient")
               [] hash patient)]

          (testing "the entry is about both bounds of `2019-11-16T23:14:29Z`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "death-date"
              :type := "Patient"
              [:v-hash lower-bound-instant] := (Instant/parse "2019-11-16T23:14:29Z")
              [:v-hash upper-bound-instant] := (Instant/parse "2019-11-16T23:14:29Z")
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash))))))

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
              (index-entries
               (sr/get search-param-registry "date" "Encounter")
               [] hash encounter)]

          (testing "the entry is about the lower bound of the start and the upper
                  bound of the end of the period"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "date"
              :type := "Encounter"
              [:v-hash lower-bound-instant] := (Instant/parse "2019-11-16T23:14:29Z")
              [:v-hash upper-bound-instant] := (Instant/parse "2019-11-16T23:44:29Z")
              :id := "id-160224"
              :hash-prefix := (hash/prefix hash))))

        (testing "without start"
          (let [encounter
                {:fhir/type :fhir/Encounter :id "id-160224"
                 :period
                 #fhir/Period
                  {:end #fhir/dateTime"2019-11-17"}}
                hash (hash/generate encounter)
                [[_ k0]]
                (index-entries
                 (sr/get search-param-registry "date" "Encounter")
                 [] hash encounter)]

            (testing "the entry is about the min bound as lower bound and the
                    upper bound of the end of the period"
              (given (sp-vr-tu/decode-key-human (bb/wrap k0))
                :code := "date"
                :type := "Encounter"
                [:v-hash lower-bound-instant] := (Instant/parse "0001-01-01T00:00:00Z")
                [:v-hash upper-bound-instant] := (Instant/parse "2019-11-17T23:59:59Z")
                :id := "id-160224"
                :hash-prefix := (hash/prefix hash)))))

        (testing "Encounter date without end"
          (let [encounter
                {:fhir/type :fhir/Encounter :id "id-160224"
                 :period
                 #fhir/Period
                  {:start #fhir/dateTime"2019-11-17T00:14:29+01:00"}}
                hash (hash/generate encounter)
                [[_ k0]]
                (index-entries
                 (sr/get search-param-registry "date" "Encounter")
                 [] hash encounter)]

            (testing "the entry is about the lower bound of the start and the max
                    upper bound"
              (given (sp-vr-tu/decode-key-human (bb/wrap k0))
                :code := "date"
                :type := "Encounter"
                [:v-hash lower-bound-instant] := (Instant/parse "2019-11-16T23:14:29Z")
                [:v-hash upper-bound-instant] := (Instant/parse "9999-12-31T23:59:59Z")
                :id := "id-160224"
                :hash-prefix := (hash/prefix hash)))))))

    (testing "DiagnosticReport"
      (testing "issued"
        (let [patient {:fhir/type :fhir/DiagnosticReport
                       :id "id-155607"
                       :issued #fhir/instant"2019-11-17T00:14:29.917+01:00"}
              hash (hash/generate patient)
              [[_ k0]]
              (index-entries
               (sr/get search-param-registry "issued" "DiagnosticReport")
               [] hash patient)]

          (testing "the entry is about both bounds of `2019-11-17T00:14:29.917+01:00`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "issued"
              :type := "DiagnosticReport"
              [:v-hash lower-bound-instant] := (Instant/parse "2019-11-16T23:14:29Z")
              [:v-hash upper-bound-instant] := (Instant/parse "2019-11-16T23:14:29Z")
              :id := "id-155607"
              :hash-prefix := (hash/prefix hash))))))

    (testing "FHIRPath evaluation problem"
      (let [resource {:fhir/type :fhir/DiagnosticReport :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                  (sr/get search-param-registry "issued" "DiagnosticReport")
                  [] hash resource)
            ::anom/category := ::anom/fault)))))

  (testing "skip warning"
    (is (nil? (spd/index-entries "" nil)))))

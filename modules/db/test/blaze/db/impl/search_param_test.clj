(ns blaze.db.impl.search-param-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [clj-fuzzy.phonetics :as phonetics]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [taoensso.timbre :as log])
  (:import
    [java.time OffsetDateTime ZoneId ZoneOffset LocalDate]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def birthdate
  (sr/get search-param-registry "birthdate" "Patient"))


(defn compile-birthdate [value]
  (first (search-param/compile-values birthdate [value])))


(deftest compile-values-test
  (testing "Date"
    (are [value op quantity] (= [op quantity] (compile-birthdate value))
      "2020-10-30" :eq (system/parse-date-time "2020-10-30"))))


(deftest index-entries-test
  (testing "Observation _id"
    (let [observation {:fhir/type :fhir/Observation
                       :id "id-161849"}
          hash (hash/generate observation)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "_id" "Observation")
            hash observation [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "_id")
                (codec/tid "Observation")
                (codec/v-hash "id-161849")
                (codec/id-bytes "id-161849")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Observation")
                (codec/id-bytes "id-161849")
                hash
                (codec/c-hash "_id")
                (codec/v-hash "id-161849")))))))

  (testing "Observation code"
    (let [observation {:fhir/type :fhir/Observation
                       :id "id-183201"
                       :code
                       {:fhir/type :fhir/CodeableConcept
                        :coding
                        [{:fhir/type :fhir/Coding
                          :system #fhir/uri"system-171339"
                          :code #fhir/code"code-171327"}]}}
          hash (hash/generate observation)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries
            (sr/get search-param-registry "code" "Observation")
            hash observation [])]

      (testing "first search-param-value-key is about `code`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "code")
                (codec/tid "Observation")
                (codec/v-hash "code-171327")
                (codec/id-bytes "id-183201")
                hash))))

      (testing "first resource-value-key is about `code`"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Observation")
                (codec/id-bytes "id-183201")
                hash
                (codec/c-hash "code")
                (codec/v-hash "code-171327")))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/sp-value-resource-key
                (codec/c-hash "code")
                (codec/tid "Observation")
                (codec/v-hash "system-171339|")
                (codec/id-bytes "id-183201")
                hash))))

      (testing "second resource-value-key is about `system|`"
        (is (bytes/=
              k3
              (codec/resource-sp-value-key
                (codec/tid "Observation")
                (codec/id-bytes "id-183201")
                hash
                (codec/c-hash "code")
                (codec/v-hash "system-171339|")))))

      (testing "third search-param-value-key is about `system|code`"
        (is (bytes/=
              k4
              (codec/sp-value-resource-key
                (codec/c-hash "code")
                (codec/tid "Observation")
                (codec/v-hash "system-171339|code-171327")
                (codec/id-bytes "id-183201")
                hash))))

      (testing "third resource-value-key is about `system|code`"
        (is (bytes/=
              k5
              (codec/resource-sp-value-key
                (codec/tid "Observation")
                (codec/id-bytes "id-183201")
                hash
                (codec/c-hash "code")
                (codec/v-hash "system-171339|code-171327")))))))


  (testing "Patient phonetic"
    (testing "missing family is not a problem"
      (let [patient {:fhir/type :fhir/Patient
                     :id "id-164114"
                     :name [{:fhir/type :fhir/HumanName}]}
            hash (hash/generate patient)]

        (is (empty? (search-param/index-entries
                      (sr/get search-param-registry "phonetic" "Patient")
                      hash patient []))))))


  (testing "Patient address"
    (let [patient {:fhir/type :fhir/Patient
                   :id "id-122929"
                   :address
                   [{:fhir/type :fhir/Address
                     :line ["line-120252"]
                     :city "city-105431"}]}
          hash (hash/generate patient)
          [[_ k0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries
            (sr/get search-param-registry "address" "Patient")
            hash patient [])]

      (testing "first entry is about `line`"
        (testing "search-param-value-key"
          (is (bytes/=
                k0
                (codec/sp-value-resource-key
                  (codec/c-hash "address")
                  (codec/tid "Patient")
                  (codec/string "line 120252")
                  (codec/id-bytes "id-122929")
                  hash))))

        (testing "resource-value-key"
          (is (bytes/=
                k1
                (codec/resource-sp-value-key
                  (codec/tid "Patient")
                  (codec/id-bytes "id-122929")
                  hash
                  (codec/c-hash "address")
                  (codec/string "line 120252"))))))

      (testing "first entry is about `city`"
        (testing "search-param-value-key"
          (is (bytes/=
                k2
                (codec/sp-value-resource-key
                  (codec/c-hash "address")
                  (codec/tid "Patient")
                  (codec/string "city 105431")
                  (codec/id-bytes "id-122929")
                  hash))))

        (testing "resource-value-key"
          (is (bytes/=
                k3
                (codec/resource-sp-value-key
                  (codec/tid "Patient")
                  (codec/id-bytes "id-122929")
                  hash
                  (codec/c-hash "address")
                  (codec/string "city 105431"))))))))

  (testing "Patient identifier"
    (let [patient {:fhir/type :fhir/Patient
                   :id "id-122929"
                   :identifier
                   [{:fhir/type :fhir/Identifier
                     :system #fhir/uri"system-123000"
                     :value "value-123005"}]}
          hash (hash/generate patient)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries
            (sr/get search-param-registry "identifier" "Patient")
            hash patient [])]

      (testing "first search-param-value-key is about `value`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "identifier")
                (codec/tid "Patient")
                (codec/v-hash "value-123005")
                (codec/id-bytes "id-122929")
                hash))))

      (testing "first resource-value-key is about `value`"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Patient")
                (codec/id-bytes "id-122929")
                hash
                (codec/c-hash "identifier")
                (codec/v-hash "value-123005")))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/sp-value-resource-key
                (codec/c-hash "identifier")
                (codec/tid "Patient")
                (codec/v-hash "system-123000|")
                (codec/id-bytes "id-122929")
                hash))))

      (testing "second resource-value-key is about `system|`"
        (is (bytes/=
              k3
              (codec/resource-sp-value-key
                (codec/tid "Patient")
                (codec/id-bytes "id-122929")
                hash
                (codec/c-hash "identifier")
                (codec/v-hash "system-123000|")))))

      (testing "third search-param-value-key is about `system|value`"
        (is (bytes/=
              k4
              (codec/sp-value-resource-key
                (codec/c-hash "identifier")
                (codec/tid "Patient")
                (codec/v-hash "system-123000|value-123005")
                (codec/id-bytes "id-122929")
                hash))))

      (testing "third resource-value-key is about `system|value`"
        (is (bytes/=
              k5
              (codec/resource-sp-value-key
                (codec/tid "Patient")
                (codec/id-bytes "id-122929")
                hash
                (codec/c-hash "identifier")
                (codec/v-hash "system-123000|value-123005")))))))

  (testing "Patient _profile"
    (let [patient {:fhir/type :fhir/Patient
                   :id "id-140855"
                   :meta
                   {:fhir/type :fhir/Meta
                    :profile
                    [#fhir/canonical"profile-uri-141443"]}}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "_profile" "Patient")
            hash patient [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "_profile")
                (codec/tid "Patient")
                (codec/v-hash "profile-uri-141443")
                (codec/id-bytes "id-140855")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Patient")
                (codec/id-bytes "id-140855")
                hash
                (codec/c-hash "_profile")
                (codec/v-hash "profile-uri-141443")))))))

  (testing "Patient phonetic"
    (let [patient {:fhir/type :fhir/Patient
                   :id "id-122929"
                   :name
                   [{:fhir/type :fhir/HumanName
                     :family "family-102508"}]}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "phonetic" "Patient")
            hash patient [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "phonetic")
                (codec/tid "Patient")
                (codec/string (phonetics/soundex "family-102508"))
                (codec/id-bytes "id-122929")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Patient")
                (codec/id-bytes "id-122929")
                hash
                (codec/c-hash "phonetic")
                (codec/string (phonetics/soundex "family-102508"))))))))

  (testing "Patient birthDate"
    (let [patient {:fhir/type :fhir/Patient
                   :id "id-142629"
                   :birthDate #fhir/date"2020-02-04"}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "birthdate" "Patient")
            hash patient [])]

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

  (testing "Patient deceased"
    (let [patient {:fhir/type :fhir/Patient
                   :id "id-142629"}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "deceased" "Patient")
            hash patient [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "deceased")
                (codec/tid "Patient")
                (codec/v-hash "false")
                (codec/id-bytes "id-142629")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Patient")
                (codec/id-bytes "id-142629")
                hash
                (codec/c-hash "deceased")
                (codec/v-hash "false")))))))

  (testing "Specimen patient will not indexed because we don't support resolving in FHIRPath"
    (let [specimen {:fhir/type :fhir/Specimen
                    :id "id-150810"
                    :subject
                    {:fhir/type :fhir/Reference
                     :reference "reference-150829"}}
          hash (hash/generate specimen)]
      (is
        (empty?
          (search-param/index-entries
            (sr/get search-param-registry "patient" "Specimen")
            hash specimen [])))))

  (testing "Specimen bodysite"
    (let [specimen {:fhir/type :fhir/Specimen
                    :id "id-105153"
                    :collection
                    {:fhir/type :fhir.Specimen/collection
                     :bodySite
                     {:fhir/type :fhir/CodeableConcept
                      :coding
                      [{:fhir/type :fhir/Coding
                        :system #fhir/uri"system-103824"
                        :code #fhir/code"code-103812"}]}}}
          hash (hash/generate specimen)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries
            (sr/get search-param-registry "bodysite" "Specimen")
            hash specimen [])]

      (testing "first search-param-value-key is about `code`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "bodysite")
                (codec/tid "Specimen")
                (codec/v-hash "code-103812")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "first resource-value-key is about `code`"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Specimen")
                (codec/id-bytes "id-105153")
                hash
                (codec/c-hash "bodysite")
                (codec/v-hash "code-103812")))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/sp-value-resource-key
                (codec/c-hash "bodysite")
                (codec/tid "Specimen")
                (codec/v-hash "system-103824|")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "second resource-value-key is about `system|`"
        (is (bytes/=
              k3
              (codec/resource-sp-value-key
                (codec/tid "Specimen")
                (codec/id-bytes "id-105153")
                hash
                (codec/c-hash "bodysite")
                (codec/v-hash "system-103824|")))))

      (testing "third search-param-value-key is about `system|code`"
        (is (bytes/=
              k4
              (codec/sp-value-resource-key
                (codec/c-hash "bodysite")
                (codec/tid "Specimen")
                (codec/v-hash "system-103824|code-103812")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "third resource-value-key is about `system|code`"
        (is (bytes/=
              k5
              (codec/resource-sp-value-key
                (codec/tid "Specimen")
                (codec/id-bytes "id-105153")
                hash
                (codec/c-hash "bodysite")
                (codec/v-hash "system-103824|code-103812")))))))

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
                (codec/date-lb (ZoneId/systemDefault) (OffsetDateTime/of 2019 11 17 0 14 29 917 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-155607")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:14:29.917+01:00`"
        (is (bytes/=
              k1
              (codec/sp-value-resource-key
                (codec/c-hash "issued")
                (codec/tid "DiagnosticReport")
                (codec/date-ub (ZoneId/systemDefault) (OffsetDateTime/of 2019 11 17 0 14 29 917 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-155607")
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
                (codec/date-lb (ZoneId/systemDefault) (OffsetDateTime/of 2019 11 17 0 14 29 0 (ZoneOffset/ofHours 1)))
                (codec/id-bytes "id-160224")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:44:29+01:00`"
        (is (bytes/=
              k1
              (codec/sp-value-resource-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-ub (ZoneId/systemDefault) (OffsetDateTime/of 2019 11 17 0 44 29 0 (ZoneOffset/ofHours 1)))
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
                (codec/date-lb (ZoneId/systemDefault) (OffsetDateTime/of 2019 11 17 0 14 29 0 (ZoneOffset/ofHours 1)))
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

  (testing "Encounter class"
    (let [specimen {:fhir/type :fhir/Encounter
                    :id "id-105153"
                    :class
                    {:fhir/type :fhir/Coding
                     :system #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ActCode"
                     :code #fhir/code"AMB"}}
          hash (hash/generate specimen)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries
            (sr/get search-param-registry "class" "Encounter")
            hash specimen [])]

      (testing "first search-param-value-key is about `code`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "class")
                (codec/tid "Encounter")
                (codec/v-hash "AMB")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "first resource-value-key is about `code`"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "Encounter")
                (codec/id-bytes "id-105153")
                hash
                (codec/c-hash "class")
                (codec/v-hash "AMB")))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/sp-value-resource-key
                (codec/c-hash "class")
                (codec/tid "Encounter")
                (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "second resource-value-key is about `system|`"
        (is (bytes/=
              k3
              (codec/resource-sp-value-key
                (codec/tid "Encounter")
                (codec/id-bytes "id-105153")
                hash
                (codec/c-hash "class")
                (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")))))

      (testing "third search-param-value-key is about `system|code`"
        (is (bytes/=
              k4
              (codec/sp-value-resource-key
                (codec/c-hash "class")
                (codec/tid "Encounter")
                (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "third resource-value-key is about `system|code`"
        (is (bytes/=
              k5
              (codec/resource-sp-value-key
                (codec/tid "Encounter")
                (codec/id-bytes "id-105153")
                hash
                (codec/c-hash "class")
                (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")))))))

  (testing "ImagingStudy series"
    (let [specimen {:fhir/type :fhir/ImagingStudy
                    :id "id-105153"
                    :series
                    [{:fhir/type :fhir.ImagingStudy/series
                      :uid #fhir/id"1.2.840.99999999.1.59354388.1582528879516"}]}
          hash (hash/generate specimen)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "series" "ImagingStudy")
            hash specimen [])]

      (testing "search-param-value-key is about `id`"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "series")
                (codec/tid "ImagingStudy")
                (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "ImagingStudy")
                (codec/id-bytes "id-105153")
                hash
                (codec/c-hash "series")
                (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")))))))

  (testing "ActivityDefinition url"
    (let [resource {:fhir/type :fhir/ActivityDefinition
                    :id "id-111846"
                    :url #fhir/uri"url-111854"}
          hash (hash/generate resource)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "url" "ActivityDefinition")
            hash resource [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "url")
                (codec/tid "ActivityDefinition")
                (codec/v-hash "url-111854")
                (codec/id-bytes "id-111846")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "ActivityDefinition")
                (codec/id-bytes "id-111846")
                hash
                (codec/c-hash "url")
                (codec/v-hash "url-111854")))))))

  (testing "ActivityDefinition description"
    (let [resource {:fhir/type :fhir/ActivityDefinition
                    :id "id-121344"
                    :description #fhir/markdown"desc-121328"}
          hash (hash/generate resource)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "description" "ActivityDefinition")
            hash resource [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "description")
                (codec/tid "ActivityDefinition")
                (codec/string "desc 121328")
                (codec/id-bytes "id-121344")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "ActivityDefinition")
                (codec/id-bytes "id-121344")
                hash
                (codec/c-hash "description")
                (codec/string "desc 121328")))))))

  (testing "CodeSystem version"
    (let [resource {:fhir/type :fhir/CodeSystem
                    :id "id-111846"
                    :version "version-122621"}
          hash (hash/generate resource)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "version" "CodeSystem")
            hash resource [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/sp-value-resource-key
                (codec/c-hash "version")
                (codec/tid "CodeSystem")
                (codec/v-hash "version-122621")
                (codec/id-bytes "id-111846")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-sp-value-key
                (codec/tid "CodeSystem")
                (codec/id-bytes "id-111846")
                hash
                (codec/c-hash "version")
                (codec/v-hash "version-122621")))))))

  (testing "List item"
    (testing "with literal reference"
      (let [resource {:fhir/type :fhir/List
                      :id "id-121825"
                      :entry
                      [{:fhir/type :fhir.List/entry
                        :item
                        {:fhir/type :fhir/Reference
                         :reference "Patient/0"}}]}
            hash (hash/generate resource)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (search-param/index-entries
              (sr/get search-param-registry "item" "List")
              hash resource [])]

        (testing "first search-param-value-key is about `id`"
          (is (bytes/=
                k0
                (codec/sp-value-resource-key
                  (codec/c-hash "item")
                  (codec/tid "List")
                  (codec/v-hash "0")
                  (codec/id-bytes "id-121825")
                  hash))))

        (testing "first resource-value-key is about `id`"
          (is (bytes/=
                k1
                (codec/resource-sp-value-key
                  (codec/tid "List")
                  (codec/id-bytes "id-121825")
                  hash
                  (codec/c-hash "item")
                  (codec/v-hash "0")))))

        (testing "second search-param-value-key is about `type/id`"
          (is (bytes/=
                k2
                (codec/sp-value-resource-key
                  (codec/c-hash "item")
                  (codec/tid "List")
                  (codec/v-hash "Patient/0")
                  (codec/id-bytes "id-121825")
                  hash))))

        (testing "second resource-value-key is about `type/id`"
          (is (bytes/=
                k3
                (codec/resource-sp-value-key
                  (codec/tid "List")
                  (codec/id-bytes "id-121825")
                  hash
                  (codec/c-hash "item")
                  (codec/v-hash "Patient/0")))))

        (testing "third search-param-value-key is about `tid` and `id`"
          (is (bytes/=
                k4
                (codec/sp-value-resource-key
                  (codec/c-hash "item")
                  (codec/tid "List")
                  (codec/tid-id (codec/tid "Patient") (codec/id-bytes "0"))
                  (codec/id-bytes "id-121825")
                  hash))))

        (testing "third resource-value-key is about `tid` and `id`"
          (is (bytes/=
                k5
                (codec/resource-sp-value-key
                  (codec/tid "List")
                  (codec/id-bytes "id-121825")
                  hash
                  (codec/c-hash "item")
                  (codec/tid-id (codec/tid "Patient") (codec/id-bytes "0"))))))))

    (testing "with identifier reference"
      (let [resource {:fhir/type :fhir/List
                      :id "id-123058"
                      :entry
                      [{:fhir/type :fhir.List/entry
                        :item
                        {:fhir/type :fhir/Reference
                         :identifier
                         {:fhir/type :fhir/Identifier
                          :system #fhir/uri"system-122917"
                          :value "value-122931"}}}]}
            hash (hash/generate resource)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (search-param/index-entries
              (sr/get search-param-registry "item" "List")
              hash resource [])]

        (testing "first search-param-value-key is about `value`"
          (is (bytes/=
                k0
                (codec/sp-value-resource-key
                  (codec/c-hash "item:identifier")
                  (codec/tid "List")
                  (codec/v-hash "value-122931")
                  (codec/id-bytes "id-123058")
                  hash))))

        (testing "first resource-value-key is about `value`"
          (is (bytes/=
                k1
                (codec/resource-sp-value-key
                  (codec/tid "List")
                  (codec/id-bytes "id-123058")
                  hash
                  (codec/c-hash "item:identifier")
                  (codec/v-hash "value-122931")))))

        (testing "second search-param-value-key is about `system|`"
          (is (bytes/=
                k2
                (codec/sp-value-resource-key
                  (codec/c-hash "item:identifier")
                  (codec/tid "List")
                  (codec/v-hash "system-122917|")
                  (codec/id-bytes "id-123058")
                  hash))))

        (testing "second resource-value-key is about `system|`"
          (is (bytes/=
                k3
                (codec/resource-sp-value-key
                  (codec/tid "List")
                  (codec/id-bytes "id-123058")
                  hash
                  (codec/c-hash "item:identifier")
                  (codec/v-hash "system-122917|")))))

        (testing "third search-param-value-key is about `system|value`"
          (is (bytes/=
                k4
                (codec/sp-value-resource-key
                  (codec/c-hash "item:identifier")
                  (codec/tid "List")
                  (codec/v-hash "system-122917|value-122931")
                  (codec/id-bytes "id-123058")
                  hash))))

        (testing "third resource-value-key is about `system|value`"
          (is (bytes/=
                k5
                (codec/resource-sp-value-key
                  (codec/tid "List")
                  (codec/id-bytes "id-123058")
                  hash
                  (codec/c-hash "item:identifier")
                  (codec/v-hash "system-122917|value-122931")))))))))

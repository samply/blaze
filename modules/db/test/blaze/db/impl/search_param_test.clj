(ns blaze.db.impl.search-param-test
  (:require
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [clj-fuzzy.phonetics :as phonetics]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.time ZoneId]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(deftest index-entries
  (testing "Observation code"
    (let [observation {:resourceType "Observation"
                       :id "id-183201"
                       :code
                       {:coding
                        [{:system "system-171339"
                          :code "code-171327"}]}}
          hash (codec/hash observation)
          [[_ k0 v0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries
            (sr/get search-param-registry "code" "Observation")
            hash observation [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-183201")
                  hash
                  (codec/c-hash "code")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (bytes/concat
                  [(codec/v-hash "code-171327")
                   (codec/v-hash "system-171339|")
                   (codec/v-hash "system-171339|code-171327")])))))

      (testing "first search-param-value-key is about `code`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "code")
                (codec/tid "Observation")
                (codec/v-hash "code-171327")
                (codec/id-bytes "id-183201")
                hash))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/search-param-value-key
                (codec/c-hash "code")
                (codec/tid "Observation")
                (codec/v-hash "system-171339|")
                (codec/id-bytes "id-183201")
                hash))))

      (testing "third search-param-value-key is about `system|code`"
        (is (bytes/=
              k3
              (codec/search-param-value-key
                (codec/c-hash "code")
                (codec/tid "Observation")
                (codec/v-hash "system-171339|code-171327")
                (codec/id-bytes "id-183201")
                hash))))))


  (testing "Patient phonetic"
    (testing "missing family is not a problem"
      (let [patient {:resourceType "Patient"
                     :id "id-164114"
                     :name [{}]}
            hash (codec/hash patient)]

        (is (empty? (search-param/index-entries
                      (sr/get search-param-registry "phonetic" "Patient")
                      hash patient []))))))


  (testing "Patient address"
    (let [patient {:resourceType "Patient"
                   :id "id-122929"
                   :address
                   [{:line ["line-120252"]
                     :city "city-105431"}]}
          hash (codec/hash patient)
          [[_ k0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries
            (sr/get search-param-registry "address" "Patient")
            hash patient [])]

      (testing "first entry is about `line`"
        (testing "search-param-value-key"
          (is (bytes/=
                k0
                (codec/search-param-value-key
                  (codec/c-hash "address")
                  (codec/tid "Patient")
                  (codec/string "line 120252")
                  (codec/id-bytes "id-122929")
                  hash))))

        (testing "resource-value-key"
          (is (bytes/=
                k1
                (codec/resource-value-key
                  (codec/tid "Patient")
                  (codec/id-bytes "id-122929")
                  hash
                  (codec/c-hash "address")
                  (codec/string "line 120252"))))))

      (testing "first entry is about `city`"
        (testing "search-param-value-key"
          (is (bytes/=
                k2
                (codec/search-param-value-key
                  (codec/c-hash "address")
                  (codec/tid "Patient")
                  (codec/string "city 105431")
                  (codec/id-bytes "id-122929")
                  hash))))

        (testing "resource-value-key"
          (is (bytes/=
                k3
                (codec/resource-value-key
                  (codec/tid "Patient")
                  (codec/id-bytes "id-122929")
                  hash
                  (codec/c-hash "address")
                  (codec/string "city 105431"))))))))

  (testing "Patient identifier"
    (let [patient {:resourceType "Patient"
                   :id "id-122929"
                   :identifier
                   [{:system "system-123000"
                     :value "value-123005"}]}
          hash (codec/hash patient)
          [[_ k0 v0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries
            (sr/get search-param-registry "identifier" "Patient")
            hash patient [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "Patient")
                  (codec/id-bytes "id-122929")
                  hash
                  (codec/c-hash "identifier")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (bytes/concat
                  [(codec/v-hash "value-123005")
                   (codec/v-hash "system-123000|")
                   (codec/v-hash "system-123000|value-123005")])))))

      (testing "first search-param-value-key is about `value`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "identifier")
                (codec/tid "Patient")
                (codec/v-hash "value-123005")
                (codec/id-bytes "id-122929")
                hash))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/search-param-value-key
                (codec/c-hash "identifier")
                (codec/tid "Patient")
                (codec/v-hash "system-123000|")
                (codec/id-bytes "id-122929")
                hash))))

      (testing "third search-param-value-key is about `system|value`"
        (is (bytes/=
              k3
              (codec/search-param-value-key
                (codec/c-hash "identifier")
                (codec/tid "Patient")
                (codec/v-hash "system-123000|value-123005")
                (codec/id-bytes "id-122929")
                hash))))))

  (testing "Patient _profile"
    (let [patient {:resourceType "Patient"
                   :id "id-140855"
                   :meta
                   {:profile
                    ["profile-uri-141443"]}}
          hash (codec/hash patient)
          [[_ k0 v0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "_profile" "Patient")
            hash patient [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "Patient")
                  (codec/id-bytes "id-140855")
                  hash
                  (codec/c-hash "_profile")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (codec/v-hash "profile-uri-141443")))))

      (testing "search-param-value-key is about the uri"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "_profile")
                (codec/tid "Patient")
                (codec/v-hash "profile-uri-141443")
                (codec/id-bytes "id-140855")
                hash))))))

  (testing "Patient phonetic"
    (let [patient {:resourceType "Patient"
                   :id "id-122929"
                   :name
                   [{:family "family-102508"}]}
          hash (codec/hash patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "phonetic" "Patient")
            hash patient [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/search-param-value-key
                (codec/c-hash "phonetic")
                (codec/tid "Patient")
                (codec/string (phonetics/soundex "family-102508"))
                (codec/id-bytes "id-122929")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-value-key
                (codec/tid "Patient")
                (codec/id-bytes "id-122929")
                hash
                (codec/c-hash "phonetic")
                (codec/string (phonetics/soundex "family-102508"))))))))

  (testing "Patient birthDate"
    (let [patient {:resourceType "Patient"
                   :id "id-142629"
                   :birthDate "2020-02-04"}
          hash (codec/hash patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "birthdate" "Patient")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2020-02-04`"
        (is (bytes/=
              k0
              (codec/search-param-value-key
                (codec/c-hash "birthdate")
                (codec/tid "Patient")
                (codec/date-lb (ZoneId/systemDefault) "2020-02-04")
                (codec/id-bytes "id-142629")
                hash))))

      (testing "the second entry is about the upper bound of `2020-02-04`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "birthdate")
                (codec/tid "Patient")
                (codec/date-ub (ZoneId/systemDefault) "2020-02-04")
                (codec/id-bytes "id-142629")
                hash))))))

  (testing "Patient deceased"
    (let [patient {:resourceType "Patient"
                   :id "id-142629"}
          hash (codec/hash patient)
          [[_ k0 v0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "deceased" "Patient")
            hash patient [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "Patient")
                  (codec/id-bytes "id-142629")
                  hash
                  (codec/c-hash "deceased")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (codec/v-hash "false")))))

      (testing "the search-param-value-key is about `false`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "deceased")
                (codec/tid "Patient")
                (codec/v-hash "false")
                (codec/id-bytes "id-142629")
                hash))))))

  (testing "Specimen patient will not indexed because we don not support resolving in FHIRPath"
    (let [specimen {:resourceType "Specimen"
                    :id "id-150810"
                    :subject {:reference "reference-150829"}}
          hash (codec/hash specimen)]
      (is
        (empty?
          (search-param/index-entries
            (sr/get search-param-registry "patient" "Specimen")
            hash specimen [])))))

  (testing "Specimen bodysite"
    (let [specimen {:resourceType "Specimen"
                    :id "id-105153"
                    :collection
                    {:bodySite
                     {:coding
                      [{:system "system-103824"
                        :code "code-103812"}]}}}
          hash (codec/hash specimen)
          [[_ k0 v0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries
            (sr/get search-param-registry "bodysite" "Specimen")
            hash specimen [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "Specimen")
                  (codec/id-bytes "id-105153")
                  hash
                  (codec/c-hash "bodysite")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (bytes/concat
                  [(codec/v-hash "code-103812")
                   (codec/v-hash "system-103824|")
                   (codec/v-hash "system-103824|code-103812")])))))

      (testing "first search-param-value-key is about `code`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "bodysite")
                (codec/tid "Specimen")
                (codec/v-hash "code-103812")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/search-param-value-key
                (codec/c-hash "bodysite")
                (codec/tid "Specimen")
                (codec/v-hash "system-103824|")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "third search-param-value-key is about `system|code`"
        (is (bytes/=
              k3
              (codec/search-param-value-key
                (codec/c-hash "bodysite")
                (codec/tid "Specimen")
                (codec/v-hash "system-103824|code-103812")
                (codec/id-bytes "id-105153")
                hash))))))

  (testing "DiagnosticReport issued"
    (let [patient {:resourceType "DiagnosticReport"
                   :id "id-155607"
                   :issued "2019-11-17T00:14:29.917+01:00"}
          hash (codec/hash patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "issued" "DiagnosticReport")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29.917+01:00`"
        (is (bytes/=
              k0
              (codec/search-param-value-key
                (codec/c-hash "issued")
                (codec/tid "DiagnosticReport")
                (codec/date-lb (ZoneId/systemDefault) "2019-11-17T00:14:29.917+01:00")
                (codec/id-bytes "id-155607")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:14:29.917+01:00`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "issued")
                (codec/tid "DiagnosticReport")
                (codec/date-ub (ZoneId/systemDefault) "2019-11-17T00:14:29.917+01:00")
                (codec/id-bytes "id-155607")
                hash))))))

  (testing "Encounter date"
    (let [patient {:resourceType "Encounter"
                   :id "id-160224"
                   :period
                   {:start "2019-11-17T00:14:29+01:00"
                    :end "2019-11-17T00:44:29+01:00"}}
          hash (codec/hash patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "date" "Encounter")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29+01:00`"
        (is (bytes/=
              k0
              (codec/search-param-value-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-lb (ZoneId/systemDefault) "2019-11-17T00:14:29+01:00")
                (codec/id-bytes "id-160224")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:44:29+01:00`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-ub (ZoneId/systemDefault) "2019-11-17T00:44:29+01:00")
                (codec/id-bytes "id-160224")
                hash))))))

  (testing "Encounter date without start"
    (let [patient {:resourceType "Encounter"
                   :id "id-160224"
                   :period
                   {:end "2019-11-17"}}
          hash (codec/hash patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "date" "Encounter")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29+01:00`"
        (is (bytes/=
              k0
              (codec/search-param-value-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                codec/date-min-bound
                (codec/id-bytes "id-160224")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-ub (ZoneId/systemDefault) "2019-11-17")
                (codec/id-bytes "id-160224")
                hash))))))

  (testing "Encounter date without end"
    (let [patient {:resourceType "Encounter"
                   :id "id-160224"
                   :period
                   {:start "2019-11-17T00:14:29+01:00"}}
          hash (codec/hash patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "date" "Encounter")
            hash patient [])]

      (testing "the first entry is about the lower bound of `2019-11-17T00:14:29+01:00`"
        (is (bytes/=
              k0
              (codec/search-param-value-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                (codec/date-lb (ZoneId/systemDefault) "2019-11-17T00:14:29+01:00")
                (codec/id-bytes "id-160224")
                hash))))

      (testing "the second entry is about the upper bound of `2019-11-17T00:44:29+01:00`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "date")
                (codec/tid "Encounter")
                codec/date-max-bound
                (codec/id-bytes "id-160224")
                hash))))))

  (testing "Encounter class"
    (let [specimen {:resourceType "Encounter"
                    :id "id-105153"
                    :class
                    {:system "http://terminology.hl7.org/CodeSystem/v3-ActCode"
                     :code "AMB"}}
          hash (codec/hash specimen)
          [[_ k0 v0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries
            (sr/get search-param-registry "class" "Encounter")
            hash specimen [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "Encounter")
                  (codec/id-bytes "id-105153")
                  hash
                  (codec/c-hash "class")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (bytes/concat
                  [(codec/v-hash "AMB")
                   (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")
                   (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")])))))

      (testing "first search-param-value-key is about `code`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "class")
                (codec/tid "Encounter")
                (codec/v-hash "AMB")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "second search-param-value-key is about `system|`"
        (is (bytes/=
              k2
              (codec/search-param-value-key
                (codec/c-hash "class")
                (codec/tid "Encounter")
                (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")
                (codec/id-bytes "id-105153")
                hash))))

      (testing "third search-param-value-key is about `system|code`"
        (is (bytes/=
              k3
              (codec/search-param-value-key
                (codec/c-hash "class")
                (codec/tid "Encounter")
                (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")
                (codec/id-bytes "id-105153")
                hash))))))

  (testing "ImagingStudy series"
    (let [specimen {:resourceType "ImagingStudy"
                    :id "id-105153"
                    :series
                    [{:uid "1.2.840.99999999.1.59354388.1582528879516"}]}
          hash (codec/hash specimen)
          [[_ k0 v0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "series" "ImagingStudy")
            hash specimen [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "ImagingStudy")
                  (codec/id-bytes "id-105153")
                  hash
                  (codec/c-hash "series")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")))))

      (testing "search-param-value-key is about `id`"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "series")
                (codec/tid "ImagingStudy")
                (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")
                (codec/id-bytes "id-105153")
                hash))))))

  (testing "ActivityDefinition url"
    (let [resource {:resourceType "ActivityDefinition"
                    :id "id-111846"
                    :url "url-111854"}
          hash (codec/hash resource)
          [[_ k0 v0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "url" "ActivityDefinition")
            hash resource [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "ActivityDefinition")
                  (codec/id-bytes "id-111846")
                  hash
                  (codec/c-hash "url")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (codec/v-hash "url-111854")))))

      (testing "search-param-value-key"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "url")
                (codec/tid "ActivityDefinition")
                (codec/v-hash "url-111854")
                (codec/id-bytes "id-111846")
                hash))))))

  (testing "ActivityDefinition description"
    (let [resource {:resourceType "ActivityDefinition"
                    :id "id-121344"
                    :description "desc-121328"}
          hash (codec/hash resource)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "description" "ActivityDefinition")
            hash resource [])]

      (testing "search-param-value-key"
        (is (bytes/=
              k0
              (codec/search-param-value-key
                (codec/c-hash "description")
                (codec/tid "ActivityDefinition")
                (codec/string "desc 121328")
                (codec/id-bytes "id-121344")
                hash))))

      (testing "resource-value-key"
        (is (bytes/=
              k1
              (codec/resource-value-key
                (codec/tid "ActivityDefinition")
                (codec/id-bytes "id-121344")
                hash
                (codec/c-hash "description")
                (codec/string "desc 121328")))))))

  (testing "CodeSystem version"
    (let [resource {:resourceType "CodeSystem"
                    :id "id-111846"
                    :version "version-122621"}
          hash (codec/hash resource)
          [[_ k0 v0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "version" "CodeSystem")
            hash resource [])]

      (testing "the resource-value entry is"
        (testing "resource-value-key"
          (is (bytes/=
                k0
                (codec/resource-value-key
                  (codec/tid "CodeSystem")
                  (codec/id-bytes "id-111846")
                  hash
                  (codec/c-hash "version")))))

        (testing "resource-value-value"
          (is (bytes/=
                v0
                (codec/v-hash "version-122621")))))

      (testing "search-param-value-key"
        (is (bytes/=
              k1
              (codec/search-param-value-key
                (codec/c-hash "version")
                (codec/tid "CodeSystem")
                (codec/v-hash "version-122621")
                (codec/id-bytes "id-111846")
                hash)))))))

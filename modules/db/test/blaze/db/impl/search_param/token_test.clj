(ns blaze.db.impl.search-param.token-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def code-param
  (sr/get search-param-registry "code" "Observation"))


(deftest name-test
  (is (= "code" (:name code-param))))


(deftest code-test
  (is (= "code" (:code code-param))))


(deftest c-hash-test
  (is (= (codec/c-hash "code") (:c-hash code-param))))


(deftest index-entries-test
  (testing "Observation _id"
    (let [observation
          {:fhir/type :fhir/Observation
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
    (let [observation
          {:fhir/type :fhir/Observation
           :id "id-183201"
           :code
           {:fhir/type :fhir/CodeableConcept
            :coding
            [{:fhir/type :fhir/Coding
              :system #fhir/uri"system-171339"
              :code #fhir/code"code-171327"}]}}
          hash (hash/generate observation)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries code-param hash observation [])]

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
                (codec/v-hash "version-122621"))))))))

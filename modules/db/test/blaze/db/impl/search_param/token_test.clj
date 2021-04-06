(ns blaze.db.impl.search-param.token-test
  (:require
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.impl.search-param.token-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn fixture [f]
  (st/instrument)
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

      (testing "SearchParamValueResource key"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "_id"
          :type := "Observation"
          :v-hash := (codec/v-hash "id-161849")
          :id := "id-161849"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "ResourceSearchParamValue key"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Observation"
          :id := "id-161849"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "_id"
          :v-hash := (codec/v-hash "id-161849")))))

  (testing "Observation code"
    (let [observation
          {:fhir/type :fhir/Observation
           :id "id-183201"
           :code
           #fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                    {:system #fhir/uri"system-171339"
                     :code #fhir/code"code-171327"}]}}
          hash (hash/generate observation)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries code-param hash observation [])]

      (testing "first SearchParamValueResource key is about `code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "code"
          :type := "Observation"
          :v-hash := (codec/v-hash "code-171327")
          :id := "id-183201"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "first ResourceSearchParamValue key is about `code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Observation"
          :id := "id-183201"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "code"
          :v-hash := (codec/v-hash "code-171327")))

      (testing "second SearchParamValueResource key is about `system|`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k2))
          :code := "code"
          :type := "Observation"
          :v-hash := (codec/v-hash "system-171339|")
          :id := "id-183201"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "second ResourceSearchParamValue key is about `system|`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
          :type := "Observation"
          :id := "id-183201"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "code"
          :v-hash := (codec/v-hash "system-171339|")))

      (testing "third SearchParamValueResource key is about `system|code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k4))
          :code := "code"
          :type := "Observation"
          :v-hash := (codec/v-hash "system-171339|code-171327")
          :id := "id-183201"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "third ResourceSearchParamValue key is about `system|code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
          :type := "Observation"
          :id := "id-183201"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "code"
          :v-hash := (codec/v-hash "system-171339|code-171327")))))

  (testing "Observation code without system"
    (let [observation
          {:fhir/type :fhir/Observation
           :id "id-183201"
           :code
           #fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                    {:code #fhir/code"code-134035"}]}}
          hash (hash/generate observation)
          [[_ k0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries code-param hash observation [])]

      (testing "first SearchParamValueResource key is about `code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "code"
          :type := "Observation"
          :v-hash := (codec/v-hash "code-134035")
          :id := "id-183201"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "first ResourceSearchParamValue key is about `code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Observation"
          :id := "id-183201"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "code"
          :v-hash := (codec/v-hash "code-134035")))

      (testing "second SearchParamValueResource key is about `|code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k2))
          :code := "code"
          :type := "Observation"
          :v-hash := (codec/v-hash "|code-134035")
          :id := "id-183201"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "second ResourceSearchParamValue key is about `|code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
          :type := "Observation"
          :id := "id-183201"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "code"
          :v-hash := (codec/v-hash "|code-134035")))))

  (testing "Observation code with system only"
    (let [observation
          {:fhir/type :fhir/Observation
           :id "id-183201"
           :code
           #fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                    {:system #fhir/uri"system-171339"}]}}
          hash (hash/generate observation)
          [[_ k0] [_ k1]]
          (search-param/index-entries code-param hash observation [])]

      (testing "first SearchParamValueResource key is about `system|`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "code"
          :type := "Observation"
          :v-hash := (codec/v-hash "system-171339|")
          :id := "id-183201"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "first ResourceSearchParamValue key is about `system|`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Observation"
          :id := "id-183201"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "code"
          :v-hash := (codec/v-hash "system-171339|")))))

  (testing "Patient identifier"
    (let [patient
          {:fhir/type :fhir/Patient :id "id-122929"
           :identifier
           [#fhir/Identifier
               {:system #fhir/uri"system-123000"
                :value "value-123005"}]}
          hash (hash/generate patient)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries
            (sr/get search-param-registry "identifier" "Patient")
            hash patient [])]

      (testing "first SearchParamValueResource key is about `value`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "identifier"
          :type := "Patient"
          :v-hash := (codec/v-hash "value-123005")
          :id := "id-122929"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "first ResourceSearchParamValue key is about `value`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Patient"
          :id := "id-122929"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "identifier"
          :v-hash := (codec/v-hash "value-123005")))

      (testing "second SearchParamValueResource key is about `system|`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k2))
          :code := "identifier"
          :type := "Patient"
          :v-hash := (codec/v-hash "system-123000|")
          :id := "id-122929"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "second ResourceSearchParamValue key is about `system|`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
          :type := "Patient"
          :id := "id-122929"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "identifier"
          :v-hash := (codec/v-hash "system-123000|")))

      (testing "third SearchParamValueResource key is about `system|value`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k4))
          :code := "identifier"
          :type := "Patient"
          :v-hash := (codec/v-hash "system-123000|value-123005")
          :id := "id-122929"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "third ResourceSearchParamValue key is about `system|value`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
          :type := "Patient"
          :id := "id-122929"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "identifier"
          :v-hash := (codec/v-hash "system-123000|value-123005")))))

  (testing "Patient identifier without system"
    (let [patient
          {:fhir/type :fhir/Patient :id "id-122929"
           :identifier
           [#fhir/Identifier
               {:value "value-140132"}]}
          hash (hash/generate patient)
          [[_ k0] [_ k1] [_ k2] [_ k3]]
          (search-param/index-entries
            (sr/get search-param-registry "identifier" "Patient")
            hash patient [])]

      (testing "first SearchParamValueResource key is about `value`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "identifier"
          :type := "Patient"
          :v-hash := (codec/v-hash "value-140132")
          :id := "id-122929"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "first ResourceSearchParamValue key is about `value`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Patient"
          :id := "id-122929"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "identifier"
          :v-hash := (codec/v-hash "value-140132")))

      (testing "third SearchParamValueResource key is about `|value`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k2))
          :code := "identifier"
          :type := "Patient"
          :v-hash := (codec/v-hash "|value-140132")
          :id := "id-122929"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "third ResourceSearchParamValue key is about `|value`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
          :type := "Patient"
          :id := "id-122929"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "identifier"
          :v-hash := (codec/v-hash "|value-140132")))))

  (testing "Patient identifier with system only"
    (let [patient
          {:fhir/type :fhir/Patient :id "id-122929"
           :identifier
           [#fhir/Identifier
               {:system #fhir/uri"system-140316"}]}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "identifier" "Patient")
            hash patient [])]

      (testing "second SearchParamValueResource key is about `system|`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "identifier"
          :type := "Patient"
          :v-hash := (codec/v-hash "system-140316|")
          :id := "id-122929"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "second ResourceSearchParamValue key is about `system|`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Patient"
          :id := "id-122929"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "identifier"
          :v-hash := (codec/v-hash "system-140316|")))))

  (testing "Patient deceased"
    (testing "no value"
      (let [patient {:fhir/type :fhir/Patient :id "id-142629"}
            hash (hash/generate patient)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (sr/get search-param-registry "deceased" "Patient")
              hash patient [])]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "deceased"
            :type := "Patient"
            :v-hash := (codec/v-hash "false")
            :id := "id-142629"
            :hash-prefix (codec/hash-prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-142629"
            :hash-prefix := (codec/hash-prefix hash)
            :code := "deceased"
            :v-hash := (codec/v-hash "false")))))

    (testing "true value"
      (let [patient {:fhir/type :fhir/Patient
                     :id "id-142629"
                     :deceased true}
            hash (hash/generate patient)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (sr/get search-param-registry "deceased" "Patient")
              hash patient [])]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "deceased"
            :type := "Patient"
            :v-hash := (codec/v-hash "true")
            :id := "id-142629"
            :hash-prefix (codec/hash-prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-142629"
            :hash-prefix := (codec/hash-prefix hash)
            :code := "deceased"
            :v-hash := (codec/v-hash "true")))))

    (testing "dateTime value"
      (let [patient
            {:fhir/type :fhir/Patient
             :id "id-142629"
             :deceased #fhir/dateTime"2019-11-17T00:14:29+01:00"}
            hash (hash/generate patient)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (sr/get search-param-registry "deceased" "Patient")
              hash patient [])]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "deceased"
            :type := "Patient"
            :v-hash := (codec/v-hash "true")
            :id := "id-142629"
            :hash-prefix (codec/hash-prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-142629"
            :hash-prefix := (codec/hash-prefix hash)
            :code := "deceased"
            :v-hash := (codec/v-hash "true"))))))

  (testing "Specimen bodysite"
    (let [specimen {:fhir/type :fhir/Specimen
                    :id "id-105153"
                    :collection
                    {:fhir/type :fhir.Specimen/collection
                     :bodySite
                     #fhir/CodeableConcept
                         {:coding
                          [#fhir/Coding
                              {:system #fhir/uri"system-103824"
                               :code #fhir/code"code-103812"}]}}}
          hash (hash/generate specimen)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries
            (sr/get search-param-registry "bodysite" "Specimen")
            hash specimen [])]

      (testing "first SearchParamValueResource key is about `code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "bodysite"
          :type := "Specimen"
          :v-hash := (codec/v-hash "code-103812")
          :id := "id-105153"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "first ResourceSearchParamValue key is about `code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Specimen"
          :id := "id-105153"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "bodysite"
          :v-hash := (codec/v-hash "code-103812")))

      (testing "second SearchParamValueResource key is about `system|`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k2))
          :code := "bodysite"
          :type := "Specimen"
          :v-hash := (codec/v-hash "system-103824|")
          :id := "id-105153"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "second ResourceSearchParamValue key is about `system|`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
          :type := "Specimen"
          :id := "id-105153"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "bodysite"
          :v-hash := (codec/v-hash "system-103824|")))

      (testing "third SearchParamValueResource key is about `system|code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k4))
          :code := "bodysite"
          :type := "Specimen"
          :v-hash := (codec/v-hash "system-103824|code-103812")
          :id := "id-105153"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "third ResourceSearchParamValue key is about `system|code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
          :type := "Specimen"
          :id := "id-105153"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "bodysite"
          :v-hash := (codec/v-hash "system-103824|code-103812")))))

  (testing "Encounter class"
    (let [specimen
          {:fhir/type :fhir/Encounter :id "id-105153"
           :class
           #fhir/Coding
               {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ActCode"
                :code #fhir/code"AMB"}}
          hash (hash/generate specimen)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
          (search-param/index-entries
            (sr/get search-param-registry "class" "Encounter")
            hash specimen [])]

      (testing "first SearchParamValueResource key is about `code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "class"
          :type := "Encounter"
          :v-hash := (codec/v-hash "AMB")
          :id := "id-105153"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "first ResourceSearchParamValue key is about `code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "Encounter"
          :id := "id-105153"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "class"
          :v-hash := (codec/v-hash "AMB")))

      (testing "second SearchParamValueResource key is about `system|`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k2))
          :code := "class"
          :type := "Encounter"
          :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")
          :id := "id-105153"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "second ResourceSearchParamValue key is about `system|`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
          :type := "Encounter"
          :id := "id-105153"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "class"
          :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")))

      (testing "third SearchParamValueResource key is about `system|code`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k4))
          :code := "class"
          :type := "Encounter"
          :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")
          :id := "id-105153"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "third ResourceSearchParamValue key is about `system|code`"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
          :type := "Encounter"
          :id := "id-105153"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "class"
          :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")))))

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

      (testing "SearchParamValueResource key is about `id`"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "series"
          :type := "ImagingStudy"
          :v-hash := (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")
          :id := "id-105153"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "ResourceSearchParamValue key"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "ImagingStudy"
          :id := "id-105153"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "series"
          :v-hash := (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")))))

  (testing "CodeSystem version"
    (let [resource {:fhir/type :fhir/CodeSystem
                    :id "id-111846"
                    :version "version-122621"}
          hash (hash/generate resource)
          [[_ k0] [_ k1]]
          (search-param/index-entries
            (sr/get search-param-registry "version" "CodeSystem")
            hash resource [])]

      (testing "SearchParamValueResource key"
        (given (sp-vr-tu/decode-key-human (bb/wrap k0))
          :code := "version"
          :type := "CodeSystem"
          :v-hash := (codec/v-hash "version-122621")
          :id := "id-111846"
          :hash-prefix (codec/hash-prefix hash)))

      (testing "ResourceSearchParamValue key"
        (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
          :type := "CodeSystem"
          :id := "id-111846"
          :hash-prefix := (codec/hash-prefix hash)
          :code := "version"
          :v-hash := (codec/v-hash "version-122621")))))

  (testing "FHIRPath evaluation problem"
    (let [resource {:fhir/type :fhir/Patient :id "foo"}
          hash (hash/generate resource)]

      (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
        (given (search-param/index-entries
                 (sr/get search-param-registry "_id" "Patient")
                 hash resource [])
          ::anom/category := ::anom/fault)))))

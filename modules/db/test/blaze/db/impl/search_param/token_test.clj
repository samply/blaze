(ns blaze.db.impl.search-param.token-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.impl.search-param.token :as spt]
    [blaze.db.impl.search-param.token-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :as tu :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(defn code-param [search-param-registry]
  (sr/get search-param-registry "code" "Observation"))


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest code-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (given (code-param search-param-registry)
      :name := "code"
      :code := "code"
      :c-hash := (codec/c-hash "code"))))


(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Observation _id"
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-161849"}
            hash (hash/generate observation)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (sr/get search-param-registry "_id" "Observation")
              (constantly nil)
              [] 153828 hash observation)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "_id"
            :type := "Observation"
            :v-hash := (codec/v-hash "id-161849")
            :did := 153828
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :did := 153828
            :hash-prefix := (hash/prefix hash)
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
            (search-param/index-entries
              (code-param search-param-registry)
              (constantly nil)
              [] 153911 hash observation)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "code-171327")
            :did := 153911
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :did := 153911
            :hash-prefix := (hash/prefix hash)
            :code := "code"
            :v-hash := (codec/v-hash "code-171327")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-171339|")
            :did := 153911
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Observation"
            :did := 153911
            :hash-prefix := (hash/prefix hash)
            :code := "code"
            :v-hash := (codec/v-hash "system-171339|")))

        (testing "third SearchParamValueResource key is about `system|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-171339|code-171327")
            :did := 153911
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Observation"
            :did := 153911
            :hash-prefix := (hash/prefix hash)
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
            (search-param/index-entries
              (code-param search-param-registry)
              (constantly nil)
              [] 153954 hash observation)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "code-134035")
            :did := 153954
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :did := 153954
            :hash-prefix := (hash/prefix hash)
            :code := "code"
            :v-hash := (codec/v-hash "code-134035")))

        (testing "second SearchParamValueResource key is about `|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "|code-134035")
            :did := 153954
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Observation"
            :did := 153954
            :hash-prefix := (hash/prefix hash)
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
            (search-param/index-entries
              (code-param search-param-registry)
              (constantly nil)
              [] 154050 hash observation)]

        (testing "first SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-171339|")
            :did := 154050
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :did := 154050
            :hash-prefix := (hash/prefix hash)
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
              (constantly nil)
              [] 154101 hash patient)]

        (testing "first SearchParamValueResource key is about `value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "value-123005")
            :did := 154101
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :did := 154101
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "value-123005")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "system-123000|")
            :did := 154101
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Patient"
            :did := 154101
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "system-123000|")))

        (testing "third SearchParamValueResource key is about `system|value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "system-123000|value-123005")
            :did := 154101
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Patient"
            :did := 154101
            :hash-prefix := (hash/prefix hash)
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
              (constantly nil)
              [] 154139 hash patient)]

        (testing "first SearchParamValueResource key is about `value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "value-140132")
            :did := 154139
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :did := 154139
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "value-140132")))

        (testing "third SearchParamValueResource key is about `|value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "|value-140132")
            :did := 154139
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `|value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Patient"
            :did := 154139
            :hash-prefix := (hash/prefix hash)
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
              (constantly nil)
              [] 154210 hash patient)]

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "system-140316|")
            :did := 154210
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :did := 154210
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "system-140316|")))))

    (testing "Patient deceased"
      (testing "no value"
        (let [patient {:fhir/type :fhir/Patient :id "id-142629"}
              hash (hash/generate patient)
              [[_ k0] [_ k1]]
              (search-param/index-entries
                (sr/get search-param-registry "deceased" "Patient")
                (constantly nil)
                [] 154245 hash patient)]

          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "deceased"
              :type := "Patient"
              :v-hash := (codec/v-hash "false")
              :did := 154245
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :did := 154245
              :hash-prefix := (hash/prefix hash)
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
                (constantly nil)
                [] 154258 hash patient)]

          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "deceased"
              :type := "Patient"
              :v-hash := (codec/v-hash "true")
              :did := 154258
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :did := 154258
              :hash-prefix := (hash/prefix hash)
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
                (constantly nil)
                [] 154315 hash patient)]

          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "deceased"
              :type := "Patient"
              :v-hash := (codec/v-hash "true")
              :did := 154315
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :did := 154315
              :hash-prefix := (hash/prefix hash)
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
              (constantly nil)
              [] 154350 hash specimen)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "bodysite"
            :type := "Specimen"
            :v-hash := (codec/v-hash "code-103812")
            :did := 154350
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Specimen"
            :did := 154350
            :hash-prefix := (hash/prefix hash)
            :code := "bodysite"
            :v-hash := (codec/v-hash "code-103812")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "bodysite"
            :type := "Specimen"
            :v-hash := (codec/v-hash "system-103824|")
            :did := 154350
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Specimen"
            :did := 154350
            :hash-prefix := (hash/prefix hash)
            :code := "bodysite"
            :v-hash := (codec/v-hash "system-103824|")))

        (testing "third SearchParamValueResource key is about `system|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "bodysite"
            :type := "Specimen"
            :v-hash := (codec/v-hash "system-103824|code-103812")
            :did := 154350
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Specimen"
            :did := 154350
            :hash-prefix := (hash/prefix hash)
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
              (constantly nil)
              [] 154421 hash specimen)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "class"
            :type := "Encounter"
            :v-hash := (codec/v-hash "AMB")
            :did := 154421
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Encounter"
            :did := 154421
            :hash-prefix := (hash/prefix hash)
            :code := "class"
            :v-hash := (codec/v-hash "AMB")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "class"
            :type := "Encounter"
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")
            :did := 154421
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Encounter"
            :did := 154421
            :hash-prefix := (hash/prefix hash)
            :code := "class"
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")))

        (testing "third SearchParamValueResource key is about `system|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "class"
            :type := "Encounter"
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")
            :did := 154421
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Encounter"
            :did := 154421
            :hash-prefix := (hash/prefix hash)
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
              (constantly nil)
              [] 154455 hash specimen)]

        (testing "SearchParamValueResource key is about `id`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "series"
            :type := "ImagingStudy"
            :v-hash := (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")
            :did := 154455
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "ImagingStudy"
            :did := 154455
            :hash-prefix := (hash/prefix hash)
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
              (constantly nil)
              [] 154546 hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "version"
            :type := "CodeSystem"
            :v-hash := (codec/v-hash "version-122621")
            :did := 154546
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "CodeSystem"
            :did := 154546
            :hash-prefix := (hash/prefix hash)
            :code := "version"
            :v-hash := (codec/v-hash "version-122621")))))

    (testing "FHIRPath evaluation problem"
      (let [resource {:fhir/type :fhir/Patient :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                   (sr/get search-param-registry "_id" "Patient")
                   (constantly nil)
                   [] 154605 hash resource)
            ::anom/category := ::anom/fault))))

    (testing "skip warning"
      (is (nil? (spt/index-entries (constantly nil) "" nil))))))


(defn subject-param [search-param-registry]
  (sr/get search-param-registry "subject" "Observation"))


(defn patient-param [search-param-registry]
  (sr/get search-param-registry "patient" "Condition"))


(defn compartment-ids [search-param resource]
  (into [] (search-param/compartment-ids search-param resource)))


(deftest compartment-ids-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Observation"
      (let [subject-param (subject-param search-param-registry)]

        (testing "with literal reference"
          (let [observation {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:reference "Patient/0"}}]
            (is (= ["0"] (compartment-ids subject-param observation)))))

        (testing "without reference"
          (let [observation {:fhir/type :fhir/Observation :id "0"}]
            (is (empty? (compartment-ids subject-param observation)))))

        (testing "with reference without reference value"
          (let [observation {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:display "foo"}}]
            (is (empty? (compartment-ids subject-param observation)))))

        (testing "with absolute reference"
          (let [observation {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:reference "http://server.org/Patient/0"}}]
            (is (empty? (compartment-ids subject-param observation)))))))

    (testing "Condition"
      (let [patient-param (patient-param search-param-registry)]

        (testing "with literal reference"
          (let [condition {:fhir/type :fhir/Condition :id "0"
                           :subject #fhir/Reference{:reference "Patient/0"}}]
            (is (= ["0"] (compartment-ids patient-param condition)))))

        (testing "without reference"
          (let [condition {:fhir/type :fhir/Condition :id "0"}]
            (is (empty? (compartment-ids patient-param condition)))))

        (testing "with reference without reference value"
          (let [condition {:fhir/type :fhir/Condition :id "0"
                           :subject #fhir/Reference{:display "foo"}}]
            (is (empty? (compartment-ids patient-param condition)))))

        (testing "with absolute reference"
          (let [condition {:fhir/type :fhir/Condition :id "0"
                           :subject #fhir/Reference{:reference "http://server.org/Patient/0"}}]
            (is (empty? (compartment-ids patient-param condition)))))))))

(ns blaze.db.impl.search-param.token-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer :as bb]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.token :as spt]
   [blaze.db.impl.search-param.token-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn code-param [search-param-registry]
  (sr/get search-param-registry "code" "Observation"))

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest code-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (code-param search-param-registry)
      :name := "code"
      :code := "code"
      :c-hash := (codec/c-hash "code"))))

(defn id-param [search-param-registry]
  (sr/get search-param-registry "_id" "Resource"))

(defn identifier-param [search-param-registry]
  (sr/get search-param-registry "identifier" "Patient"))

(deftest ordered-compartment-index-handles-test
  (testing "id params"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (let [search-param (id-param search-param-registry)]
        (is (false? (p/-supports-ordered-compartment-index-handles search-param nil)))
        (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil)))
        (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil))))))

  (testing "identifier params"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (let [search-param (identifier-param search-param-registry)]
        (is (false? (p/-supports-ordered-compartment-index-handles search-param nil)))
        (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil)))
        (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil)))))))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Observation _id"
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-161849"}
            hash (hash/generate observation)]

        (testing "id's don't need to be indexed"
          (is (coll/empty? (search-param/index-entries
                            (sr/get search-param-registry "_id" "Observation")
                            [] hash observation))))))

    (testing "Observation _profile"
      (let [observation
            {:fhir/type :fhir/Observation :id "id-165627"
             :meta #fhir/Meta{:profile [#fhir/canonical "uri-091902|2.3.9"]}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [_ k6] [_ k7]]
            (index-entries (sr/get search-param-registry "_profile" "Observation")
                           [] hash observation)]

        (testing "first SearchParamValueResource key is about `url|version`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "_profile"
            :type := "Observation"
            :v-hash := (codec/v-hash "uri-091902|2.3.9")
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `url|version`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)
            :code := "_profile"
            :v-hash := (codec/v-hash "uri-091902|2.3.9")))

        (testing "second SearchParamValueResource key is about `url`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "_profile:below"
            :type := "Observation"
            :v-hash := (codec/v-hash "uri-091902")
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `url`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Observation"
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)
            :code := "_profile:below"
            :v-hash := (codec/v-hash "uri-091902")))

        (testing "third SearchParamValueResource key is about `url|major-version`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "_profile:below"
            :type := "Observation"
            :v-hash := (codec/v-hash "uri-091902|2")
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `url|major-version`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Observation"
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)
            :code := "_profile:below"
            :v-hash := (codec/v-hash "uri-091902|2")))

        (testing "fourth SearchParamValueResource key is about `url|major-version.minor-version`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k6))
            :code := "_profile:below"
            :type := "Observation"
            :v-hash := (codec/v-hash "uri-091902|2.3")
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)))

        (testing "fourth ResourceSearchParamValue key is about `url|major-version.minor-version`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k7))
            :type := "Observation"
            :id := "id-165627"
            :hash-prefix := (hash/prefix hash)
            :code := "_profile:below"
            :v-hash := (codec/v-hash "uri-091902|2.3")))))

    (testing "Observation code"
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-183201"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri "system-171339"
                  :code #fhir/code "code-171327"}]}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (index-entries
             (code-param search-param-registry) [] hash observation)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "code-171327")
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :code := "code"
            :v-hash := (codec/v-hash "code-171327")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-171339|")
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Observation"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :code := "code"
            :v-hash := (codec/v-hash "system-171339|")))

        (testing "third SearchParamValueResource key is about `system|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-171339|code-171327")
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Observation"
            :id := "id-183201"
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
                 {:code #fhir/code "code-134035"}]}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3]]
            (index-entries
             (code-param search-param-registry) [] hash observation)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "code-134035")
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :code := "code"
            :v-hash := (codec/v-hash "code-134035")))

        (testing "second SearchParamValueResource key is about `|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "|code-134035")
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Observation"
            :id := "id-183201"
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
                 {:system #fhir/uri "system-171339"}]}}
            hash (hash/generate observation)
            [[_ k0] [_ k1]]
            (index-entries
             (code-param search-param-registry) [] hash observation)]

        (testing "first SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-171339|")
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :code := "code"
            :v-hash := (codec/v-hash "system-171339|")))))

    (testing "Patient active"
      (doseq [active [true false]]
        (let [patient
              {:fhir/type :fhir/Patient :id "id-122929"
               :active (type/boolean active)}
              hash (hash/generate patient)
              [[_ k0] [_ k1]]
              (index-entries
               (sr/get search-param-registry "active" "Patient")
               [] hash patient)]

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "active"
              :type := "Patient"
              :v-hash := (codec/v-hash (str active))
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)
              :code := "active"
              :v-hash := (codec/v-hash (str active))))))

      (testing "boolean without values doesn't produce index entries"
        (let [patient
              {:fhir/type :fhir/Patient :id "id-122929"
               :active #fhir/boolean{:id "foo"}}
              hash (hash/generate patient)]
          (is (empty? (index-entries
                       (sr/get search-param-registry "active" "Patient")
                       [] hash patient))))))

    (testing "Patient identifier"
      (let [patient
            {:fhir/type :fhir/Patient :id "id-122929"
             :identifier
             [#fhir/Identifier
               {:system #fhir/uri "system-123000"
                :value #fhir/string "value-123005"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (index-entries
             (sr/get search-param-registry "identifier" "Patient")
             [] hash patient)]

        (testing "first SearchParamValueResource key is about `value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "value-123005")
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "value-123005")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "system-123000|")
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Patient"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "system-123000|")))

        (testing "third SearchParamValueResource key is about `system|value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "system-123000|value-123005")
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Patient"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "system-123000|value-123005")))))

    (testing "Patient identifier without system"
      (let [patient
            {:fhir/type :fhir/Patient :id "id-122929"
             :identifier
             [#fhir/Identifier
               {:value #fhir/string "value-140132"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1] [_ k2] [_ k3]]
            (index-entries
             (sr/get search-param-registry "identifier" "Patient")
             [] hash patient)]

        (testing "first SearchParamValueResource key is about `value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "value-140132")
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "value-140132")))

        (testing "third SearchParamValueResource key is about `|value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "|value-140132")
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `|value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Patient"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "|value-140132")))))

    (testing "Patient identifier with system only"
      (let [patient
            {:fhir/type :fhir/Patient :id "id-122929"
             :identifier
             [#fhir/Identifier
               {:system #fhir/uri "system-140316"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "identifier" "Patient")
             [] hash patient)]

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "identifier"
            :type := "Patient"
            :v-hash := (codec/v-hash "system-140316|")
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :code := "identifier"
            :v-hash := (codec/v-hash "system-140316|")))))

    (testing "Patient deceased"
      (testing "no value"
        (let [patient {:fhir/type :fhir/Patient :id "id-142629"}
              hash (hash/generate patient)
              [[_ k0] [_ k1]]
              (index-entries
               (sr/get search-param-registry "deceased" "Patient")
               [] hash patient)]

          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "deceased"
              :type := "Patient"
              :v-hash := (codec/v-hash "false")
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)
              :code := "deceased"
              :v-hash := (codec/v-hash "false")))))

      (testing "true value"
        (let [patient {:fhir/type :fhir/Patient
                       :id "id-142629"
                       :deceased #fhir/boolean true}
              hash (hash/generate patient)
              [[_ k0] [_ k1]]
              (index-entries
               (sr/get search-param-registry "deceased" "Patient")
               [] hash patient)]

          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "deceased"
              :type := "Patient"
              :v-hash := (codec/v-hash "true")
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)
              :code := "deceased"
              :v-hash := (codec/v-hash "true")))))

      (testing "dateTime value"
        (let [patient
              {:fhir/type :fhir/Patient
               :id "id-142629"
               :deceased #fhir/dateTime "2019-11-17T00:14:29+01:00"}
              hash (hash/generate patient)
              [[_ k0] [_ k1]]
              (index-entries
               (sr/get search-param-registry "deceased" "Patient")
               [] hash patient)]

          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "deceased"
              :type := "Patient"
              :v-hash := (codec/v-hash "true")
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :id := "id-142629"
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
                           {:system #fhir/uri "system-103824"
                            :code #fhir/code "code-103812"}]}}}
            hash (hash/generate specimen)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (index-entries
             (sr/get search-param-registry "bodysite" "Specimen")
             [] hash specimen)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "bodysite"
            :type := "Specimen"
            :v-hash := (codec/v-hash "code-103812")
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Specimen"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :code := "bodysite"
            :v-hash := (codec/v-hash "code-103812")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "bodysite"
            :type := "Specimen"
            :v-hash := (codec/v-hash "system-103824|")
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Specimen"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :code := "bodysite"
            :v-hash := (codec/v-hash "system-103824|")))

        (testing "third SearchParamValueResource key is about `system|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "bodysite"
            :type := "Specimen"
            :v-hash := (codec/v-hash "system-103824|code-103812")
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Specimen"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :code := "bodysite"
            :v-hash := (codec/v-hash "system-103824|code-103812")))))

    (testing "Encounter class"
      (let [specimen
            {:fhir/type :fhir/Encounter :id "id-105153"
             :class
             #fhir/Coding
              {:system #fhir/uri "http://terminology.hl7.org/CodeSystem/v3-ActCode"
               :code #fhir/code "AMB"}}
            hash (hash/generate specimen)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (index-entries
             (sr/get search-param-registry "class" "Encounter")
             [] hash specimen)]

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "class"
            :type := "Encounter"
            :v-hash := (codec/v-hash "AMB")
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Encounter"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :code := "class"
            :v-hash := (codec/v-hash "AMB")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "class"
            :type := "Encounter"
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Encounter"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :code := "class"
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|")))

        (testing "third SearchParamValueResource key is about `system|code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "class"
            :type := "Encounter"
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Encounter"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :code := "class"
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")))))

    (testing "ImagingStudy series"
      (let [specimen {:fhir/type :fhir/ImagingStudy
                      :id "id-105153"
                      :series
                      [{:fhir/type :fhir.ImagingStudy/series
                        :uid #fhir/id "1.2.840.99999999.1.59354388.1582528879516"}]}
            hash (hash/generate specimen)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "series" "ImagingStudy")
             [] hash specimen)]

        (testing "SearchParamValueResource key is about `id`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "series"
            :type := "ImagingStudy"
            :v-hash := (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "ImagingStudy"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :code := "series"
            :v-hash := (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")))))

    (testing "CodeSystem version"
      (let [resource {:fhir/type :fhir/CodeSystem
                      :id "id-111846"
                      :version #fhir/string "version-122621"}
            hash (hash/generate resource)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "version" "CodeSystem")
             [] hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "version"
            :type := "CodeSystem"
            :v-hash := (codec/v-hash "version-122621")
            :id := "id-111846"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "CodeSystem"
            :id := "id-111846"
            :hash-prefix := (hash/prefix hash)
            :code := "version"
            :v-hash := (codec/v-hash "version-122621")))))

    (testing "FHIRPath evaluation problem"
      (let [resource {:fhir/type :fhir/Patient :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                  (sr/get search-param-registry "code" "Observation")
                  [] hash resource)
            ::anom/category := ::anom/fault))))

    (testing "skip warning"
      (is (nil? (spt/index-entries "" nil))))))

(defn subject-param [search-param-registry]
  (sr/get search-param-registry "subject" "Observation"))

(defn patient-param [search-param-registry]
  (sr/get search-param-registry "patient" "Condition"))

(defn compartment-ids [search-param resource]
  (vec (search-param/compartment-ids search-param resource)))

(deftest compartment-ids-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Observation"
      (let [subject-param (subject-param search-param-registry)]

        (testing "with literal reference"
          (let [observation {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            (is (= ["0"] (compartment-ids subject-param observation)))))

        (testing "without reference"
          (let [observation {:fhir/type :fhir/Observation :id "0"}]
            (is (empty? (compartment-ids subject-param observation)))))

        (testing "with reference without reference value"
          (let [observation {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:display #fhir/string "foo"}}]
            (is (empty? (compartment-ids subject-param observation)))))

        (testing "with absolute reference"
          (let [observation {:fhir/type :fhir/Observation :id "0"
                             :subject #fhir/Reference{:reference #fhir/string "http://server.org/Patient/0"}}]
            (is (empty? (compartment-ids subject-param observation)))))))

    (testing "Condition"
      (let [patient-param (patient-param search-param-registry)]

        (testing "with literal reference"
          (let [condition {:fhir/type :fhir/Condition :id "0"
                           :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
            (is (= ["0"] (compartment-ids patient-param condition)))))

        (testing "without reference"
          (let [condition {:fhir/type :fhir/Condition :id "0"}]
            (is (empty? (compartment-ids patient-param condition)))))

        (testing "with reference without reference value"
          (let [condition {:fhir/type :fhir/Condition :id "0"
                           :subject #fhir/Reference{:display #fhir/string "foo"}}]
            (is (empty? (compartment-ids patient-param condition)))))

        (testing "with absolute reference"
          (let [condition {:fhir/type :fhir/Condition :id "0"
                           :subject #fhir/Reference{:reference #fhir/string "http://server.org/Patient/0"}}]
            (is (empty? (compartment-ids patient-param condition)))))))))

(defn profile-param [search-param-registry]
  (sr/get search-param-registry "_profile" "Observation"))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "_id"
      (testing "unknown modifier"
        (given (search-param/validate-modifier
                (id-param search-param-registry) "unknown")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `unknown` on search parameter `_id`.")))

    (testing "identifier"
      (testing "unknown modifier"
        (given (search-param/validate-modifier
                (identifier-param search-param-registry) "unknown")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `unknown` on search parameter `identifier`."))

      (testing "modifier not implemented"
        (given (search-param/validate-modifier
                (identifier-param search-param-registry) "of-type")
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported modifier `of-type` on search parameter `identifier`.")))

    (testing "uri"
      (testing "unknown modifier"
        (given (search-param/validate-modifier
                (profile-param search-param-registry) "unknown")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `unknown` on search parameter `_profile`."))

      (testing "modifier not implemented"
        (given (search-param/validate-modifier
                (profile-param search-param-registry) "missing")
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported modifier `missing` on search parameter `_profile`."))

      (testing "implemented modifier"
        (is (nil? (search-param/validate-modifier (profile-param search-param-registry) "below")))))

    (testing "token"
      (testing "unknown modifier"
        (given (search-param/validate-modifier
                (code-param search-param-registry) "unknown")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `unknown` on search parameter `code`."))

      (testing "modifier not implemented"
        (given (search-param/validate-modifier
                (code-param search-param-registry) "code-text")
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported modifier `code-text` on search parameter `code`.")))

    (testing "reference"
      (testing "unknown modifier"
        (given (search-param/validate-modifier
                (subject-param search-param-registry) "unknown")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `unknown` on search parameter `subject`."))

      (testing "modifier not implemented"
        (given (search-param/validate-modifier
                (subject-param search-param-registry) "contains")
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported modifier `contains` on search parameter `subject`."))

      (testing "implemented modifier Location"
        (is (nil? (search-param/validate-modifier
                   (subject-param search-param-registry) "Location"))))

      (testing "unknown modifier Organization"
        (given (search-param/validate-modifier
                (subject-param search-param-registry) "Organization")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `Organization` on search parameter `subject`."))

      (testing "implemented modifier identifier"
        (is (nil? (search-param/validate-modifier (subject-param search-param-registry) "identifier"))))

      (testing "implemented modifier Organization"
        (let [[search-param] (sr/parse search-param-registry "Observation" "performer")]
          (is (nil? (search-param/validate-modifier
                     search-param "Organization"))))))))

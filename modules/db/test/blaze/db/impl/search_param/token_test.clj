(ns blaze.db.impl.search-param.token-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string]
   [blaze.coll.core :as coll]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.compartment.search-param-value-resource-test-util :as c-sp-vr-tu]
   [blaze.db.impl.index.patient-type-search-param-token-full-resource-spec]
   [blaze.db.impl.index.patient-type-search-param-token-full-resource-test-util :as pt-sp-tfr-tu]
   [blaze.db.impl.index.resource-search-param-token-full-spec]
   [blaze.db.impl.index.resource-search-param-token-full-test-util :as r-sp-tf-tu]
   [blaze.db.impl.index.resource-search-param-token-system-spec]
   [blaze.db.impl.index.resource-search-param-token-system-test-util :as r-sp-ts-tu]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.impl.index.type-search-param-token-full-resource-spec]
   [blaze.db.impl.index.type-search-param-token-full-resource-test-util :as t-sp-tfr-tu]
   [blaze.db.impl.index.type-search-param-token-system-resource-spec]
   [blaze.db.impl.index.type-search-param-token-system-resource-test-util :as t-sp-tsr-tu]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.search-param-code-registry-spec]
   [blaze.db.impl.search-param.token :as spt]
   [blaze.db.impl.search-param.token-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.references-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.db/search-param-registry
   {:kv-store (ig/ref ::kv/mem)
    :structure-definition-repo structure-definition-repo}
   ::kv/mem
   {:column-families
    {:search-param-code nil
     :system nil}}})

(defn- status-param [search-param-registry]
  (sr/get search-param-registry "status" "Observation"))

(defn- code-param [search-param-registry]
  (sr/get search-param-registry "code" "Observation"))

(deftest code-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (code-param search-param-registry)
      :name := "code"
      :code := "code"
      :c-hash := (codec/c-hash "code")
      :code-id := #blaze/byte-string"000022")))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (testing "Observation _id"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-161849"}
            hash (hash/generate observation)]

        (testing "id's don't need to be indexed"
          (is (coll/empty? (search-param/index-entries
                            (sr/get search-param-registry "_id" "Observation")
                            [] hash observation)))))))

  (testing "Observation status with compartment index"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-125442"
             :status #fhir/code"final"}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [i2 k2] [i3 k3] [i4 k4] [i5 k5] :as index-entries]
            (index-entries
             (status-param search-param-registry)
             [["Patient" "patient-id-125818"]]
             hash observation)]

        (is (= 6 (count index-entries)))

        (testing "first SearchParamValueResource key is about `code`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "status"
            :type := "Observation"
            :v-hash := (codec/v-hash "final")
            :id := "id-125442"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `code`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-125442"
            :hash-prefix := (hash/prefix hash)
            :code := "status"
            :v-hash := (codec/v-hash "final")))

        (testing "first CompartmentSearchParamValue key is about `code`"
          (is (= :compartment-search-param-value-index i2))
          (given (c-sp-vr-tu/decode-key-human (bb/wrap k2))
            :compartment := ["Patient" "patient-id-125818"]
            :code := "status"
            :type := "Observation"
            :v-hash := (codec/v-hash "final")
            :id := "id-125442"
            :hash-prefix := (hash/prefix hash)))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i3))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k3))
            :tb := 96
            :search-param-code := "status"
            :value := "final"
            :system := nil
            :id := "id-125442"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i4))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k4))
            :tb := 96
            :id := "id-125442"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "status"
            :value := "final"
            :system := nil))

        (testing "PatientTypeSearchParamTokenFullResource key"
          (is (= :patient-type-search-param-token-full-resource-index i5))
          (given (pt-sp-tfr-tu/decode-key-human kv-store (bb/wrap k5))
            :patient-id := "patient-id-125818"
            :tb := 96
            :search-param-code := "status"
            :value := "final"
            :system := nil
            :id := "id-125442"
            :hash-prefix := (hash/prefix hash))))))

  (testing "Observation code"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
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
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [i6 k6] [i7 k7] [i8 k8]
             [i9 k9] :as index-entries]
            (index-entries
             (code-param search-param-registry) [] hash observation)]

        (is (= 10 (count index-entries)))

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
            :v-hash := (codec/v-hash "system-171339|code-171327")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i6))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k6))
            :tb := 96
            :search-param-code := "code"
            :value := "code-171327"
            :system := "system-171339"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i7))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k7))
            :tb := 96
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "code"
            :value := "code-171327"
            :system := "system-171339"))

        (testing "TypeSearchParamTokenSystemResource key"
          (is (= :type-search-param-token-system-resource-index i8))
          (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k8))
            :tb := 96
            :search-param-code := "code"
            :system := "system-171339"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenSystem key"
          (is (= :resource-search-param-token-system-index i9))
          (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k9))
            :tb := 96
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "code"
            :system := "system-171339")))))

  (testing "Observation code with compartment index"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
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
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [i6 k6] [i7 k7] [i8 k8]
             [i9 k9] [i10 k10] [i11 k11] :as index-entries]
            (index-entries
             (code-param search-param-registry)
             [["Patient" "patient-id-131514"]] hash observation)]

        (is (= 12 (count index-entries)))

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
            :v-hash := (codec/v-hash "system-171339|code-171327")))

        (testing "first CompartmentSearchParamValue key is about `system|code`"
          (is (= :compartment-search-param-value-index i6))
          (given (c-sp-vr-tu/decode-key-human (bb/wrap k6))
            :compartment := ["Patient" "patient-id-131514"]
            :code := "code"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-171339|code-171327")
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i7))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k7))
            :tb := 96
            :search-param-code := "code"
            :value := "code-171327"
            :system := "system-171339"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i8))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k8))
            :tb := 96
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "code"
            :value := "code-171327"
            :system := "system-171339"))

        (testing "TypeSearchParamTokenSystemResource key"
          (is (= :type-search-param-token-system-resource-index i9))
          (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k9))
            :tb := 96
            :search-param-code := "code"
            :system := "system-171339"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenSystem key"
          (is (= :resource-search-param-token-system-index i10))
          (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k10))
            :tb := 96
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "code"
            :system := "system-171339"))

        (testing "PatientTypeSearchParamTokenFullResource key"
          (is (= :patient-type-search-param-token-full-resource-index i11))
          (given (pt-sp-tfr-tu/decode-key-human kv-store (bb/wrap k11))
            :patient-id := "patient-id-131514"
            :tb := 96
            :search-param-code := "code"
            :value := "code-171327"
            :system := "system-171339"
            :id := "id-183201"
            :hash-prefix := (hash/prefix hash))))))

  (testing "Observation code without system"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (doseq [linked-compartments [[] [["Patient" "patient-id-foo"]]]]
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-183201"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:code #fhir/code"code-134035"}]}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3] [i4 k4] [i5 k5] :as index-entries]
              (index-entries
               (code-param search-param-registry) linked-compartments
               hash observation)]

          (is (= 6 (count index-entries)))

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
              :v-hash := (codec/v-hash "|code-134035")))

          (testing "TypeSearchParamTokenFullResource key"
            (is (= :type-search-param-token-full-resource-index i4))
            (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k4))
              :tb := 96
              :search-param-code := "code"
              :value := "code-134035"
              :system := nil
              :id := "id-183201"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamTokenFull key"
            (is (= :resource-search-param-token-full-index i5))
            (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k5))
              :tb := 96
              :id := "id-183201"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "code"
              :value := "code-134035"
              :system := nil))))))

  (testing "Observation code with system only"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (doseq [linked-compartments [[] [["Patient" "patient-id-foo"]]]]
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-124909"
               :code
               #fhir/CodeableConcept
                {:coding
                 [#fhir/Coding
                   {:system #fhir/uri"system-124853"}]}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
              (index-entries
               (code-param search-param-registry) linked-compartments
               hash observation)]

          (is (= 4 (count index-entries)))

          (testing "first SearchParamValueResource key is about `system|`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "code"
              :type := "Observation"
              :v-hash := (codec/v-hash "system-124853|")
              :id := "id-124909"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `system|`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-124909"
              :hash-prefix := (hash/prefix hash)
              :code := "code"
              :v-hash := (codec/v-hash "system-124853|")))

          (testing "TypeSearchParamTokenSystemResource key"
            (is (= :type-search-param-token-system-resource-index i2))
            (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k2))
              :tb := 96
              :search-param-code := "code"
              :system := "system-124853"
              :id := "id-124909"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamTokenSystem key"
            (is (= :resource-search-param-token-system-index i3))
            (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k3))
              :tb := 96
              :id := "id-124909"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "code"
              :system := "system-124853"))))))

  (testing "Observation code with display only"
    (with-system [{:blaze.db/keys [search-param-registry]} config]
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-124909"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:display "foo"}]}}
            hash (hash/generate observation)]
        (is (empty? (index-entries (code-param search-param-registry) [] hash
                                   observation))))))

  (testing "Patient active"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (doseq [active [#fhir/boolean true #fhir/boolean false]]
        (let [patient
              {:fhir/type :fhir/Patient :id "id-122929"
               :active active}
              hash (hash/generate patient)
              [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
              (index-entries
               (sr/get search-param-registry "active" "Patient")
               [] hash patient)]

          (is (= 4 (count index-entries)))

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "active"
              :type := "Patient"
              :v-hash := (codec/v-hash (str (type/value active)))
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)
              :code := "active"
              :v-hash := (codec/v-hash (str (type/value active)))))

          (testing "TypeSearchParamTokenFullResource key"
            (is (= :type-search-param-token-full-resource-index i2))
            (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k2))
              :tb := 103
              :search-param-code := "active"
              :value := (str (type/value active))
              :system := nil
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamTokenFull key"
            (is (= :resource-search-param-token-full-index i3))
            (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k3))
              :tb := 103
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "active"
              :value := (str (type/value active))
              :system := nil))))

      (testing "boolean without values doesn't produce index entries"
        (let [patient
              {:fhir/type :fhir/Patient :id "id-122929"
               :active #fhir/boolean{:id "foo"}}
              hash (hash/generate patient)]
          (is (empty? (index-entries
                       (sr/get search-param-registry "active" "Patient")
                       [] hash patient)))))))

  (testing "Patient identifier"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [patient
            {:fhir/type :fhir/Patient :id "id-122929"
             :identifier
             [#fhir/Identifier
               {:system #fhir/uri"system-123000"
                :value #fhir/string"value-123005"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [i6 k6] [i7 k7] [i8 k8]
             [i9 k9] :as index-entries]
            (index-entries
             (sr/get search-param-registry "identifier" "Patient")
             [] hash patient)]

        (is (= 10 (count index-entries)))

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
            :v-hash := (codec/v-hash "system-123000|value-123005")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i6))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k6))
            :tb := 103
            :search-param-code := "identifier"
            :value := "value-123005"
            :system := "system-123000"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i7))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k7))
            :tb := 103
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "identifier"
            :value := "value-123005"
            :system := "system-123000"))

        (testing "TypeSearchParamTokenSystemResource key"
          (is (= :type-search-param-token-system-resource-index i8))
          (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k8))
            :tb := 103
            :search-param-code := "identifier"
            :system := "system-123000"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenSystem key"
          (is (= :resource-search-param-token-system-index i9))
          (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k9))
            :tb := 103
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "identifier"
            :system := "system-123000")))))

  (testing "Patient identifier without system"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [patient
            {:fhir/type :fhir/Patient :id "id-122929"
             :identifier
             [#fhir/Identifier
               {:value #fhir/string"value-140132"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1] [_ k2] [_ k3] [i4 k4] [i5 k5] :as index-entries]
            (index-entries
             (sr/get search-param-registry "identifier" "Patient")
             [] hash patient)]

        (is (= 6 (count index-entries)))

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
            :v-hash := (codec/v-hash "|value-140132")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i4))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k4))
            :tb := 103
            :search-param-code := "identifier"
            :value := "value-140132"
            :system := nil
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i5))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k5))
            :tb := 103
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "identifier"
            :value := "value-140132"
            :system := nil)))))

  (testing "Patient identifier with system only"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [patient
            {:fhir/type :fhir/Patient :id "id-122929"
             :identifier
             [#fhir/Identifier
               {:system #fhir/uri"system-140316"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
            (index-entries
             (sr/get search-param-registry "identifier" "Patient")
             [] hash patient)]

        (is (= 4 (count index-entries)))

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
            :v-hash := (codec/v-hash "system-140316|")))

        (testing "TypeSearchParamTokenSystemResource key"
          (is (= :type-search-param-token-system-resource-index i2))
          (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k2))
            :tb := 103
            :search-param-code := "identifier"
            :system := "system-140316"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenSystem key"
          (is (= :resource-search-param-token-system-index i3))
          (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k3))
            :tb := 103
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "identifier"
            :system := "system-140316")))))

  (testing "Patient deceased"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (testing "no value"
        (let [patient {:fhir/type :fhir/Patient :id "id-142629"}
              hash (hash/generate patient)
              [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
              (index-entries
               (sr/get search-param-registry "deceased" "Patient")
               [] hash patient)]

          (is (= 4 (count index-entries)))

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
              :v-hash := (codec/v-hash "false")))

          (testing "TypeSearchParamTokenFullResource key"
            (is (= :type-search-param-token-full-resource-index i2))
            (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k2))
              :tb := 103
              :search-param-code := "deceased"
              :value := "false"
              :system := nil
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamTokenFull key"
            (is (= :resource-search-param-token-full-index i3))
            (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k3))
              :tb := 103
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "deceased"
              :value := "false"
              :system := nil))))

      (testing "true value"
        (let [patient {:fhir/type :fhir/Patient
                       :id "id-142629"
                       :deceased true}
              hash (hash/generate patient)
              [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
              (index-entries
               (sr/get search-param-registry "deceased" "Patient")
               [] hash patient)]

          (is (= 4 (count index-entries)))

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
              :v-hash := (codec/v-hash "true")))

          (testing "TypeSearchParamTokenFullResource key"
            (is (= :type-search-param-token-full-resource-index i2))
            (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k2))
              :tb := 103
              :search-param-code := "deceased"
              :value := "true"
              :system := nil
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamTokenFull key"
            (is (= :resource-search-param-token-full-index i3))
            (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k3))
              :tb := 103
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "deceased"
              :value := "true"
              :system := nil))))

      (testing "dateTime value"
        (let [patient
              {:fhir/type :fhir/Patient
               :id "id-142629"
               :deceased #fhir/dateTime"2019-11-17T00:14:29+01:00"}
              hash (hash/generate patient)
              [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
              (index-entries
               (sr/get search-param-registry "deceased" "Patient")
               [] hash patient)]

          (is (= 4 (count index-entries)))

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
              :v-hash := (codec/v-hash "true")))

          (testing "TypeSearchParamTokenFullResource key"
            (is (= :type-search-param-token-full-resource-index i2))
            (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k2))
              :tb := 103
              :search-param-code := "deceased"
              :value := "true"
              :system := nil
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamTokenFull key"
            (is (= :resource-search-param-token-full-index i3))
            (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k3))
              :tb := 103
              :id := "id-142629"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "deceased"
              :value := "true"
              :system := nil))))))

  (testing "Specimen bodysite"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
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
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [i6 k6] [i7 k7] [i8 k8]
             [i9 k9] :as index-entries]
            (index-entries
             (sr/get search-param-registry "bodysite" "Specimen")
             [] hash specimen)]

        (is (= 10 (count index-entries)))

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
            :v-hash := (codec/v-hash "system-103824|code-103812")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i6))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k6))
            :tb := 126
            :search-param-code := "bodysite"
            :value := "code-103812"
            :system := "system-103824"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i7))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k7))
            :tb := 126
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "bodysite"
            :value := "code-103812"
            :system := "system-103824"))

        (testing "TypeSearchParamTokenSystemResource key"
          (is (= :type-search-param-token-system-resource-index i8))
          (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k8))
            :tb := 126
            :search-param-code := "bodysite"
            :system := "system-103824"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenSystem key"
          (is (= :resource-search-param-token-system-index i9))
          (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k9))
            :tb := 126
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "bodysite"
            :system := "system-103824")))))

  (testing "Encounter class"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [specimen
            {:fhir/type :fhir/Encounter :id "id-105153"
             :class
             #fhir/Coding
              {:system #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ActCode"
               :code #fhir/code"AMB"}}
            hash (hash/generate specimen)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [i6 k6] [i7 k7] [i8 k8]
             [i9 k9] :as index-entries]
            (index-entries
             (sr/get search-param-registry "class" "Encounter")
             [] hash specimen)]

        (is (= 10 (count index-entries)))

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
            :v-hash := (codec/v-hash "http://terminology.hl7.org/CodeSystem/v3-ActCode|AMB")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i6))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k6))
            :tb := 44
            :search-param-code := "class"
            :value := "AMB"
            :system := "http://terminology.hl7.org/CodeSystem/v3-ActCode"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i7))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k7))
            :tb := 44
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "class"
            :value := "AMB"
            :system := "http://terminology.hl7.org/CodeSystem/v3-ActCode"))

        (testing "TypeSearchParamTokenSystemResource key"
          (is (= :type-search-param-token-system-resource-index i8))
          (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k8))
            :tb := 44
            :search-param-code := "class"
            :system := "http://terminology.hl7.org/CodeSystem/v3-ActCode"
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenSystem key"
          (is (= :resource-search-param-token-system-index i9))
          (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k9))
            :tb := 44
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "class"
            :system := "http://terminology.hl7.org/CodeSystem/v3-ActCode")))))

  (testing "ImagingStudy series"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [specimen {:fhir/type :fhir/ImagingStudy
                      :id "id-105153"
                      :series
                      [{:fhir/type :fhir.ImagingStudy/series
                        :uid #fhir/id"1.2.840.99999999.1.59354388.1582528879516"}]}
            hash (hash/generate specimen)
            [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
            (index-entries
             (sr/get search-param-registry "series" "ImagingStudy")
             [] hash specimen)]

        (is (= 4 (count index-entries)))

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
            :v-hash := (codec/v-hash "1.2.840.99999999.1.59354388.1582528879516")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i2))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k2))
            :tb := 61
            :search-param-code := "series"
            :value := "1.2.840.99999999.1.59354388.1582528879516"
            :system := nil
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i3))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k3))
            :tb := 61
            :id := "id-105153"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "series"
            :value := "1.2.840.99999999.1.59354388.1582528879516"
            :system := nil)))))

  (testing "CodeSystem version"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [resource {:fhir/type :fhir/CodeSystem
                      :id "id-111846"
                      :version "version-122621"}
            hash (hash/generate resource)
            [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
            (index-entries
             (sr/get search-param-registry "version" "CodeSystem")
             [] hash resource)]

        (is (= 4 (count index-entries)))

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
            :v-hash := (codec/v-hash "version-122621")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i2))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k2))
            :tb := 22
            :search-param-code := "version"
            :value := "version-122621"
            :system := nil
            :id := "id-111846"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i3))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k3))
            :tb := 22
            :id := "id-111846"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "version"
            :value := "version-122621"
            :system := nil)))))

  (with-system [{:blaze.db/keys [search-param-registry]} config]
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

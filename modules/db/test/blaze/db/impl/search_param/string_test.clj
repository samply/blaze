(ns blaze.db.impl.search-param.string-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string-spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.impl.search-param.string :as sps]
    [blaze.db.impl.search-param.string-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :as tu :refer [with-system]]
    [clj-fuzzy.phonetics :as phonetics]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(defn phonetic-param [search-param-registry]
  (sr/get search-param-registry "phonetic" "Patient"))


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest phonetic-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (given (phonetic-param search-param-registry)
      :name := "phonetic"
      :code := "phonetic"
      :c-hash := (codec/c-hash "phonetic"))))


(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Patient phonetic"
      (testing "missing family is not a problem"
        (let [patient {:fhir/type :fhir/Patient
                       :id "id-164114"
                       :name [#fhir/HumanName{}]}
              hash (hash/generate patient)]

          (is (empty? (search-param/index-entries
                        (phonetic-param search-param-registry) [] hash
                        patient)))))

      (let [patient {:fhir/type :fhir/Patient
                     :id "id-122929"
                     :name [#fhir/HumanName{:family "family-102508"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (phonetic-param search-param-registry) [] hash patient)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "phonetic"
            :type := "Patient"
            :v-hash := (codec/string (phonetics/soundex "family-102508"))
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-122929"
            :hash-prefix := (hash/prefix hash)
            :code := "phonetic"
            :v-hash := (codec/string (phonetics/soundex "family-102508"))))))

    (testing "Patient address"
      (let [patient {:fhir/type :fhir/Patient
                     :id "id-122929"
                     :address
                     [#fhir/Address{:line ["line-120252"]
                                    :city "city-105431"}]}
            hash (hash/generate patient)
            [[_ k0] [_ k1] [_ k2] [_ k3]]
            (search-param/index-entries
              (sr/get search-param-registry "address" "Patient")
              [] hash patient)]

        (testing "first entry is about `line`"
          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "address"
              :type := "Patient"
              :v-hash := (codec/string "line 120252")
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Patient"
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)
              :code := "address"
              :v-hash := (codec/string "line 120252"))))

        (testing "first entry is about `city`"
          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "address"
              :type := "Patient"
              :v-hash := (codec/string "city 105431")
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "Patient"
              :id := "id-122929"
              :hash-prefix := (hash/prefix hash)
              :code := "address"
              :v-hash := (codec/string "city 105431"))))))

    (testing "ActivityDefinition description"
      (let [resource {:fhir/type :fhir/ActivityDefinition
                      :id "id-121344"
                      :description #fhir/markdown"desc-121328"}
            hash (hash/generate resource)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (sr/get search-param-registry "description" "ActivityDefinition")
              [] hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "description"
            :type := "ActivityDefinition"
            :v-hash := (codec/string "desc 121328")
            :id := "id-121344"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "ActivityDefinition"
            :id := "id-121344"
            :hash-prefix := (hash/prefix hash)
            :code := "description"
            :v-hash := (codec/string "desc 121328")))))

    (testing "FHIRPath evaluation problem"
      (let [resource {:fhir/type :fhir/ActivityDefinition :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                   (sr/get search-param-registry "description" "ActivityDefinition")
                   [] hash resource)
            ::anom/category := ::anom/fault))))

    (testing "skip warning"
      (is (nil? (sps/index-entries "" nil))))))

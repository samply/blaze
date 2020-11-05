(ns blaze.db.impl.search-param.string-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [clj-fuzzy.phonetics :as phonetics]
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


(def phonetic-param
  (sr/get search-param-registry "phonetic" "Patient"))


(deftest code-test
  (is (= "phonetic" (:code phonetic-param))))


(deftest index-entries-test
  (testing "Patient phonetic"
    (testing "missing family is not a problem"
      (let [patient {:fhir/type :fhir/Patient
                     :id "id-164114"
                     :name [{:fhir/type :fhir/HumanName}]}
            hash (hash/generate patient)]

        (is (empty? (search-param/index-entries phonetic-param
                                                hash patient [])))))

    (let [patient {:fhir/type :fhir/Patient
                   :id "id-122929"
                   :name
                   [{:fhir/type :fhir/HumanName
                     :family "family-102508"}]}
          hash (hash/generate patient)
          [[_ k0] [_ k1]]
          (search-param/index-entries phonetic-param hash patient [])]

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
                (codec/string "desc 121328"))))))))

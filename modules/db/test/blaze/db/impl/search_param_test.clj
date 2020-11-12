(ns blaze.db.impl.search-param-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [taoensso.timbre :as log]))


(st/instrument)


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
  (first (search-param/compile-values birthdate nil [value])))


(deftest compile-value-test
  (testing "Date"
    (are [value op quantity] (= [op quantity] (compile-birthdate value))
      "2020-10-30" :eq (system/parse-date-time "2020-10-30"))))


(deftest index-entries-test
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

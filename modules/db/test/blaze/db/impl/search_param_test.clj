(ns blaze.db.impl.search-param-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.spec.type.system :as system]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]])
  (:import
    [java.time ZoneId]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn birthdate [search-param-registry]
  (sr/get search-param-registry "birthdate" "Patient"))


(defn compile-birthdate [search-param-registry value]
  (first (search-param/compile-values (birthdate search-param-registry) nil [value])))


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Date"
      (are [value op lower-bound upper-bound]
        (given (compile-birthdate search-param-registry value)
          :op := op
          :lower-bound := lower-bound
          :upper-bound := upper-bound)
        "2020-10-30"
        :eq
        (codec/date-lb (ZoneId/systemDefault) (system/parse-date-time "2020-10-30"))
        (codec/date-ub (ZoneId/systemDefault) (system/parse-date-time "2020-10-30"))))))


(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Patient _profile"
      (let [patient
            {:fhir/type :fhir/Patient :id "id-140855"
             :meta #fhir/Meta{:profile [#fhir/canonical"profile-uri-141443"]}}
            hash (hash/generate patient)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (sr/get search-param-registry "_profile" "Patient")
              [] hash patient)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "_profile"
            :type := "Patient"
            :v-hash := (codec/v-hash "profile-uri-141443")
            :id := "id-140855"
            :hash-prefix (codec/hash-prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-140855"
            :hash-prefix := (codec/hash-prefix hash)
            :code := "_profile"
            :v-hash := (codec/v-hash "profile-uri-141443")))))

    (testing "Specimen patient will not indexed because we don't support resolving in FHIRPath"
      (let [specimen {:fhir/type :fhir/Specimen :id "id-150810"
                      :subject #fhir/Reference{:reference "reference-150829"}}
            hash (hash/generate specimen)]
        (is
          (empty?
            (search-param/index-entries
              (sr/get search-param-registry "patient" "Specimen")
              [] hash specimen)))))

    (testing "ActivityDefinition url"
      (let [resource {:fhir/type :fhir/ActivityDefinition
                      :id "id-111846"
                      :url #fhir/uri"url-111854"}
            hash (hash/generate resource)
            [[_ k0] [_ k1]]
            (search-param/index-entries
              (sr/get search-param-registry "url" "ActivityDefinition")
              [] hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "url"
            :type := "ActivityDefinition"
            :v-hash := (codec/v-hash "url-111854")
            :id := "id-111846"
            :hash-prefix (codec/hash-prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "ActivityDefinition"
            :id := "id-111846"
            :hash-prefix := (codec/hash-prefix hash)
            :code := "url"
            :v-hash := (codec/v-hash "url-111854")))))

    (testing "List item"
      (testing "with literal reference"
        (let [resource {:fhir/type :fhir/List :id "id-121825"
                        :entry
                        [{:fhir/type :fhir.List/entry
                          :item #fhir/Reference{:reference "Patient/0"}}]}
              hash (hash/generate resource)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (search-param/index-entries
                (sr/get search-param-registry "item" "List")
                [] hash resource)]

          (testing "first SearchParamValueResource key is about `id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "item"
              :type := "List"
              :v-hash := (codec/v-hash "0")
              :id := "id-121825"
              :hash-prefix (codec/hash-prefix hash)))

          (testing "first ResourceSearchParamValue key is about `id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (codec/hash-prefix hash)
              :code := "item"
              :v-hash := (codec/v-hash "0")))

          (testing "second SearchParamValueResource key is about `type/id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "item"
              :type := "List"
              :v-hash := (codec/v-hash "Patient/0")
              :id := "id-121825"
              :hash-prefix (codec/hash-prefix hash)))

          (testing "second ResourceSearchParamValue key is about `type/id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (codec/hash-prefix hash)
              :code := "item"
              :v-hash := (codec/v-hash "Patient/0")))

          (testing "third SearchParamValueResource key is about `tid` and `id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k4))
              :code := "item"
              :type := "List"
              :v-hash := (codec/tid-id (codec/tid "Patient")
                                       (codec/id-byte-string "0"))
              :id := "id-121825"
              :hash-prefix (codec/hash-prefix hash)))

          (testing "third ResourceSearchParamValue key is about `tid` and `id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (codec/hash-prefix hash)
              :code := "item"
              :v-hash := (codec/tid-id (codec/tid "Patient")
                                       (codec/id-byte-string "0"))))))

      (testing "with identifier reference"
        (let [resource {:fhir/type :fhir/List :id "id-123058"
                        :entry
                        [{:fhir/type :fhir.List/entry
                          :item
                          #fhir/Reference
                                  {:identifier
                                   #fhir/Identifier
                                           {:system #fhir/uri"system-122917"
                                            :value "value-122931"}}}]}
              hash (hash/generate resource)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (search-param/index-entries
                (sr/get search-param-registry "item" "List")
                [] hash resource)]

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "item:identifier"
              :type := "List"
              :v-hash := (codec/v-hash "value-122931")
              :id := "id-123058"
              :hash-prefix (codec/hash-prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "List"
              :id := "id-123058"
              :hash-prefix := (codec/hash-prefix hash)
              :code := "item:identifier"
              :v-hash := (codec/v-hash "value-122931")))

          (testing "second SearchParamValueResource key is about `system|`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "item:identifier"
              :type := "List"
              :v-hash := (codec/v-hash "system-122917|")
              :id := "id-123058"
              :hash-prefix (codec/hash-prefix hash)))

          (testing "second ResourceSearchParamValue key is about `system|`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "List"
              :id := "id-123058"
              :hash-prefix := (codec/hash-prefix hash)
              :code := "item:identifier"
              :v-hash := (codec/v-hash "system-122917|")))

          (testing "third SearchParamValueResource key is about `system|value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k4))
              :code := "item:identifier"
              :type := "List"
              :v-hash := (codec/v-hash "system-122917|value-122931")
              :id := "id-123058"
              :hash-prefix (codec/hash-prefix hash)))

          (testing "third ResourceSearchParamValue key is about `system|value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
              :type := "List"
              :id := "id-123058"
              :hash-prefix := (codec/hash-prefix hash)
              :code := "item:identifier"
              :v-hash := (codec/v-hash "system-122917|value-122931")))))

      (testing "with literal absolute URL reference"
        (let [resource {:fhir/type :fhir/List :id "id-121825"
                        :entry
                        [{:fhir/type :fhir.List/entry
                          :item
                          #fhir/Reference
                                  {:reference "http://foo.com/bar-141221"}}]}
              hash (hash/generate resource)
              [[_ k0] [_ k1]]
              (search-param/index-entries
                (sr/get search-param-registry "item" "List")
                [] hash resource)]

          (testing "first SearchParamValueResource key is about `id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "item"
              :type := "List"
              :v-hash := (codec/v-hash "http://foo.com/bar-141221")
              :id := "id-121825"
              :hash-prefix (codec/hash-prefix hash)))

          (testing "first ResourceSearchParamValue key is about `id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (codec/hash-prefix hash)
              :code := "item"
              :v-hash := (codec/v-hash "http://foo.com/bar-141221"))))))))

(ns blaze.db.impl.search-param-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec.date :as codec-date]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest create-test
  (testing "missing expression"
    (doseq [type ["date" "number" "quantity" "reference" "string" "token" "uri"]]
      (given (sc/search-param nil {:type type :url "url-165259"})
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported search parameter with URL `url-165259`. Required expression is missing.")))

  (testing "invalid expression"
    (doseq [type ["date" "number" "quantity" "reference" "string" "token" "uri"]]
      (given (sc/search-param nil {:type type :expression ""})
        ::anom/category := ::anom/fault
        ::anom/message := "Error while parsing token `<EOF>` in expression ``"))))

(defn birthdate [search-param-registry]
  (sr/get search-param-registry "birthdate" "Patient"))

(defn compile-birthdate [search-param-registry value]
  (first (search-param/compile-values (birthdate search-param-registry) nil [value])))

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}
   ::ts/not-available {}})

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Date"
      (given (compile-birthdate search-param-registry "2020-10-30")
        :op := :eq
        :lower-bound := (codec-date/encode-lower-bound #system/date-time"2020-10-30")
        :upper-bound := (codec-date/encode-upper-bound #system/date-time"2020-10-30")))))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Patient _profile"
      (let [patient
            {:fhir/type :fhir/Patient :id "id-140855"
             :meta #fhir/Meta{:profile [#fhir/canonical "profile-uri-141443"]}}
            hash (hash/generate patient)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "_profile" "Patient")
             [] hash patient)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "_profile"
            :type := "Patient"
            :v-hash := (codec/v-hash "profile-uri-141443")
            :id := "id-140855"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Patient"
            :id := "id-140855"
            :hash-prefix := (hash/prefix hash)
            :code := "_profile"
            :v-hash := (codec/v-hash "profile-uri-141443")))))

    (testing "Specimen patient will not indexed because we don't support resolving in FHIRPath"
      (let [specimen {:fhir/type :fhir/Specimen :id "id-150810"
                      :subject #fhir/Reference{:reference #fhir/string "reference-150829"}}
            hash (hash/generate specimen)]
        (is
         (empty?
          (index-entries
           (sr/get search-param-registry "patient" "Specimen")
           [] hash specimen)))))

    (testing "ActivityDefinition url"
      (let [resource {:fhir/type :fhir/ActivityDefinition
                      :id "id-111846"
                      :url #fhir/uri "url-111854"}
            hash (hash/generate resource)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "url" "ActivityDefinition")
             [] hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "url"
            :type := "ActivityDefinition"
            :v-hash := (codec/v-hash "url-111854")
            :id := "id-111846"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "ActivityDefinition"
            :id := "id-111846"
            :hash-prefix := (hash/prefix hash)
            :code := "url"
            :v-hash := (codec/v-hash "url-111854")))))

    (testing "List item"
      (testing "with literal reference"
        (let [resource {:fhir/type :fhir/List :id "id-121825"
                        :entry
                        [{:fhir/type :fhir.List/entry
                          :item #fhir/Reference{:reference #fhir/string "Patient/0"}}]}
              hash (hash/generate resource)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (index-entries
               (sr/get search-param-registry "item" "List")
               [] hash resource)]

          (testing "first SearchParamValueResource key is about `id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "item"
              :type := "List"
              :v-hash := (codec/v-hash "0")
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)
              :code := "item"
              :v-hash := (codec/v-hash "0")))

          (testing "second SearchParamValueResource key is about `type/id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "item"
              :type := "List"
              :v-hash := (codec/v-hash "Patient/0")
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `type/id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)
              :code := "item"
              :v-hash := (codec/v-hash "Patient/0")))

          (testing "third SearchParamValueResource key is about `tid` and `id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k4))
              :code := "item"
              :type := "List"
              :v-hash := (codec/tid-id (codec/tid "Patient")
                                       (codec/id-byte-string "0"))
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)))

          (testing "third ResourceSearchParamValue key is about `tid` and `id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)
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
                             {:system #fhir/uri "system-122917"
                              :value #fhir/string "value-122931"}}}]}
              hash (hash/generate resource)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (index-entries
               (sr/get search-param-registry "item" "List")
               [] hash resource)]

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "item:identifier"
              :type := "List"
              :v-hash := (codec/v-hash "value-122931")
              :id := "id-123058"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "List"
              :id := "id-123058"
              :hash-prefix := (hash/prefix hash)
              :code := "item:identifier"
              :v-hash := (codec/v-hash "value-122931")))

          (testing "second SearchParamValueResource key is about `system|`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "item:identifier"
              :type := "List"
              :v-hash := (codec/v-hash "system-122917|")
              :id := "id-123058"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `system|`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "List"
              :id := "id-123058"
              :hash-prefix := (hash/prefix hash)
              :code := "item:identifier"
              :v-hash := (codec/v-hash "system-122917|")))

          (testing "third SearchParamValueResource key is about `system|value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k4))
              :code := "item:identifier"
              :type := "List"
              :v-hash := (codec/v-hash "system-122917|value-122931")
              :id := "id-123058"
              :hash-prefix := (hash/prefix hash)))

          (testing "third ResourceSearchParamValue key is about `system|value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
              :type := "List"
              :id := "id-123058"
              :hash-prefix := (hash/prefix hash)
              :code := "item:identifier"
              :v-hash := (codec/v-hash "system-122917|value-122931")))))

      (testing "with literal absolute URL reference"
        (let [resource {:fhir/type :fhir/List :id "id-121825"
                        :entry
                        [{:fhir/type :fhir.List/entry
                          :item
                          #fhir/Reference
                           {:reference #fhir/string "http://foo.com/bar-141221"}}]}
              hash (hash/generate resource)
              [[_ k0] [_ k1]]
              (index-entries
               (sr/get search-param-registry "item" "List")
               [] hash resource)]

          (testing "first SearchParamValueResource key is about `id`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "item"
              :type := "List"
              :v-hash := (codec/v-hash "http://foo.com/bar-141221")
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `id`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "List"
              :id := "id-121825"
              :hash-prefix := (hash/prefix hash)
              :code := "item"
              :v-hash := (codec/v-hash "http://foo.com/bar-141221"))))))

    (testing "Encounter rank"
      (let [resource {:fhir/type :fhir/Encounter :id "id-094518"
                      :diagnosis [{:fhir/type :fhir.Encounter/diagnosis
                                   :rank #fhir/positiveInt 94656}]}
            hash (hash/generate resource)
            [[_ k0] [_ k1]]
            (index-entries
             (sc/search-param
              {}
              {:type "number"
               :name "rank"
               :code "rank"
               :base ["Encounter"]
               :url "Encounter-rank",
               :expression "Encounter.diagnosis.rank"})
             [] hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "rank"
            :type := "Encounter"
            :v-hash := (codec/number (BigDecimal/valueOf 94656))
            :id := "id-094518"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Encounter"
            :id := "id-094518"
            :hash-prefix := (hash/prefix hash)
            :code := "rank"
            :v-hash := (codec/number (BigDecimal/valueOf 94656))))))

    (testing "Appointment priority"
      (let [resource {:fhir/type :fhir/Appointment :id "id-102236"
                      :priority #fhir/unsignedInt 102229}
            hash (hash/generate resource)
            [[_ k0] [_ k1]]
            (index-entries
             (sc/search-param
              {}
              {:type "number"
               :name "priority"
               :code "priority"
               :base ["Appointment"]
               :url "Appointment-priority",
               :expression "Appointment.priority"})
             [] hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "priority"
            :type := "Appointment"
            :v-hash := (codec/number (BigDecimal/valueOf 102229))
            :id := "id-102236"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Appointment"
            :id := "id-102236"
            :hash-prefix := (hash/prefix hash)
            :code := "priority"
            :v-hash := (codec/number (BigDecimal/valueOf 102229))))))))

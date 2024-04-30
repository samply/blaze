(ns blaze.db.impl.search-param.reference-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.byte-string]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-search-param-reference-local-spec]
   [blaze.db.impl.index.resource-search-param-reference-local-test-util :as r-sp-rl-tu]
   [blaze.db.impl.index.resource-search-param-reference-url-spec]
   [blaze.db.impl.index.resource-search-param-reference-url-test-util :as r-sp-ru-tu]
   [blaze.db.impl.index.resource-search-param-token-full-spec]
   [blaze.db.impl.index.resource-search-param-token-full-test-util :as r-sp-tf-tu]
   [blaze.db.impl.index.resource-search-param-token-system-spec]
   [blaze.db.impl.index.resource-search-param-token-system-test-util :as r-sp-ts-tu]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.impl.index.type-search-param-reference-local-resource-spec]
   [blaze.db.impl.index.type-search-param-reference-local-resource-test-util :as t-sp-rlr-tu]
   [blaze.db.impl.index.type-search-param-reference-url-resource-spec]
   [blaze.db.impl.index.type-search-param-reference-url-resource-test-util :as t-sp-rur-tu]
   [blaze.db.impl.index.type-search-param-token-full-resource-test-util :as t-sp-tfr-tu]
   [blaze.db.impl.index.type-search-param-token-system-resource-test-util :as t-sp-tsr-tu]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.reference]
   [blaze.db.impl.search-param.reference-spec]
   [blaze.db.impl.search-param.search-param-code-registry-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.references-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.db/search-param-registry
   {:kv-store (ig/ref ::kv/mem)
    :structure-definition-repo structure-definition-repo}
   ::kv/mem
   {:column-families
    {:search-param-code nil
     :system nil}}})

(defn- code-param [search-param-registry]
  (sr/get search-param-registry "code" "Observation"))

(defn- subject-param [search-param-registry]
  (sr/get search-param-registry "subject" "Observation"))

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
  (testing "Observation _profile"
    (testing "with version"
      (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
        (let [observation
              {:fhir/type :fhir/Observation :id "id-165627"
               :meta #fhir/Meta{:profile [#fhir/canonical"http://example.com/profile-uri-091902|2.3.9"]}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [_ k6] [_ k7] [i8 k8]
               [i9 k9] :as index-entries]
              (index-entries (sr/get search-param-registry "_profile" "Observation")
                             [] hash observation)]

          (is (= 10 (count index-entries)))

          (testing "first SearchParamValueResource key is about `url|version`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "_profile"
              :type := "Observation"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902|2.3.9")
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `url|version`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :code := "_profile"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902|2.3.9")))

          (testing "second SearchParamValueResource key is about `url`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "_profile:below"
              :type := "Observation"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902")
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `url`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "Observation"
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :code := "_profile:below"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902")))

          (testing "third SearchParamValueResource key is about `url|major-version`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k4))
              :code := "_profile:below"
              :type := "Observation"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902|2")
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "third ResourceSearchParamValue key is about `url|major-version`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
              :type := "Observation"
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :code := "_profile:below"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902|2")))

          (testing "fourth SearchParamValueResource key is about `url|major-version.minor-version`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k6))
              :code := "_profile:below"
              :type := "Observation"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902|2.3")
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "fourth ResourceSearchParamValue key is about `url|major-version.minor-version`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k7))
              :type := "Observation"
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :code := "_profile:below"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902|2.3")))

          (testing "TypeSearchParamReferenceUrlResource key"
            (is (= :type-search-param-reference-url-resource-index i8))
            (given (t-sp-rur-tu/decode-key-human kv-store (bb/wrap k8))
              :tb := 96
              :search-param-code := "_profile"
              :url := "http://example.com/profile-uri-091902"
              :version := "2.3.9"
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamReferenceUrl key"
            (is (= :resource-search-param-reference-url-index i9))
            (given (r-sp-ru-tu/decode-key-human kv-store (bb/wrap k9))
              :tb := 96
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "_profile"
              :url := "http://example.com/profile-uri-091902"
              :version := "2.3.9")))))

    (testing "without version"
      (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
        (let [observation
              {:fhir/type :fhir/Observation :id "id-165627"
               :meta #fhir/Meta{:profile [#fhir/canonical"http://example.com/profile-uri-091902"]}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3] [i4 k4] [i5 k5] :as index-entries]
              (index-entries (sr/get search-param-registry "_profile" "Observation")
                             [] hash observation)]

          (is (= 6 (count index-entries)))

          (testing "first SearchParamValueResource key is about `url`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "_profile"
              :type := "Observation"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902")
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `url`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :code := "_profile"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902")))

          (testing "second SearchParamValueResource key is about `url`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "_profile:below"
              :type := "Observation"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902")
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `url`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "Observation"
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :code := "_profile:below"
              :v-hash := (codec/v-hash "http://example.com/profile-uri-091902")))

          (testing "TypeSearchParamReferenceUrlResource key"
            (is (= :type-search-param-reference-url-resource-index i4))
            (given (t-sp-rur-tu/decode-key-human kv-store (bb/wrap k4))
              :tb := 96
              :search-param-code := "_profile"
              :url := "http://example.com/profile-uri-091902"
              :version := ""
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamReferenceUrl key"
            (is (= :resource-search-param-reference-url-index i5))
            (given (r-sp-ru-tu/decode-key-human kv-store (bb/wrap k5))
              :tb := 96
              :id := "id-165627"
              :hash-prefix := (hash/prefix hash)
              :search-param-code := "_profile"
              :url := "http://example.com/profile-uri-091902"
              :version := ""))))))

  (testing "Observation subject local reference"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-142142"
             :subject #fhir/Reference{:reference "Patient/id-162139"}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [i6 k6] [i7 k7]
             :as index-entries]
            (index-entries
             (subject-param search-param-registry) [] hash observation)]

        (is (= 8 (count index-entries)))

        (testing "first SearchParamValueResource key is about `id`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "subject"
            :type := "Observation"
            :v-hash := (codec/v-hash "id-162139")
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `id`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)
            :code := "subject"
            :v-hash := (codec/v-hash "id-162139")))

        (testing "second SearchParamValueResource key is about `type/id`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "subject"
            :type := "Observation"
            :v-hash := (codec/v-hash "Patient/id-162139")
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `type/id`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Observation"
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)
            :code := "subject"
            :v-hash := (codec/v-hash "Patient/id-162139")))

        (testing "third SearchParamValueResource key is about `tid/id`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "subject"
            :type := "Observation"
            :v-hash := (codec/tid-id (codec/tid "Patient")
                                     (codec/id-byte-string "id-162139"))
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `tid/id`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Observation"
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)
            :code := "subject"
            :v-hash := (codec/tid-id (codec/tid "Patient")
                                     (codec/id-byte-string "id-162139"))))

        (testing "TypeSearchParamReferenceLocalResource key"
          (is (= :type-search-param-reference-local-resource-index i6))
          (given (t-sp-rlr-tu/decode-key-human kv-store (bb/wrap k6))
            :tb := 96
            :search-param-code := "subject"
            :ref-id := "id-162139"
            :ref-tb := 103
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamReferenceLocal key"
          (is (= :resource-search-param-reference-local-index i7))
          (given (r-sp-rl-tu/decode-key-human kv-store (bb/wrap k7))
            :tb := 96
            :id := "id-142142"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "subject"
            :ref-id := "id-162139"
            :ref-tb := 103)))))

  (testing "Observation subject URL reference"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [url "http://example.com/fhir/Patient/id-162139"
            observation
            {:fhir/type :fhir/Observation
             :id "id-153907"
             :subject (type/map->Reference {:reference url})}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [i2 k2] [i3 k3] :as index-entries]
            (index-entries
             (subject-param search-param-registry) [] hash observation)]

        (is (= 4 (count index-entries)))

        (testing "first SearchParamValueResource key is about `url`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "subject"
            :type := "Observation"
            :v-hash := (codec/v-hash url)
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `url`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)
            :code := "subject"
            :v-hash := (codec/v-hash url)))

        (testing "TypeSearchParamReferenceUrlResource key"
          (is (= :type-search-param-reference-url-resource-index i2))
          (given (t-sp-rur-tu/decode-key-human kv-store (bb/wrap k2))
            :tb := 96
            :search-param-code := "subject"
            :url := url
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamReferenceUrl key"
          (is (= :resource-search-param-reference-url-index i3))
          (given (r-sp-ru-tu/decode-key-human kv-store (bb/wrap k3))
            :tb := 96
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "subject"
            :url url)))))

  (testing "Observation subject reference with identifier"
    (with-system [{:blaze.db/keys [search-param-registry] kv-store ::kv/mem} config]
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-153907"
             :subject
             #fhir/Reference
              {:identifier
               #fhir/Identifier{:system #fhir/uri"system-123000"
                                :value "value-123005"}}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5] [i6 k6] [i7 k7] [i8 k8]
             [i9 k9] :as index-entries]
            (index-entries
             (subject-param search-param-registry) [] hash observation)]

        (is (= 10 (count index-entries)))

        (testing "first SearchParamValueResource key is about `value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "subject:identifier"
            :type := "Observation"
            :v-hash := (codec/v-hash "value-123005")
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)
            :code := "subject:identifier"
            :v-hash := (codec/v-hash "value-123005")))

        (testing "second SearchParamValueResource key is about `system|`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            :code := "subject:identifier"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-123000|")
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)))

        (testing "second ResourceSearchParamValue key is about `system|`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            :type := "Observation"
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)
            :code := "subject:identifier"
            :v-hash := (codec/v-hash "system-123000|")))

        (testing "third SearchParamValueResource key is about `system|value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            :code := "subject:identifier"
            :type := "Observation"
            :v-hash := (codec/v-hash "system-123000|value-123005")
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)))

        (testing "third ResourceSearchParamValue key is about `system|value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            :type := "Observation"
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)
            :code := "subject:identifier"
            :v-hash := (codec/v-hash "system-123000|value-123005")))

        (testing "TypeSearchParamTokenFullResource key"
          (is (= :type-search-param-token-full-resource-index i6))
          (given (t-sp-tfr-tu/decode-key-human kv-store (bb/wrap k6))
            :tb := 96
            :search-param-code := "subject"
            :value := "value-123005"
            :system := "system-123000"
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenFull key"
          (is (= :resource-search-param-token-full-index i7))
          (given (r-sp-tf-tu/decode-key-human kv-store (bb/wrap k7))
            :tb := 96
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "subject"
            :value := "value-123005"
            :system := "system-123000"))

        (testing "TypeSearchParamTokenSystemResource key"
          (is (= :type-search-param-token-system-resource-index i8))
          (given (t-sp-tsr-tu/decode-key-human kv-store (bb/wrap k8))
            :tb := 96
            :search-param-code := "subject"
            :system := "system-123000"
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamTokenSystem key"
          (is (= :resource-search-param-token-system-index i9))
          (given (r-sp-ts-tu/decode-key-human kv-store (bb/wrap k9))
            :tb := 96
            :id := "id-153907"
            :hash-prefix := (hash/prefix hash)
            :search-param-code := "subject"
            :system := "system-123000"))))))

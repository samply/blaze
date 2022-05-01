(ns blaze.db.impl.search-param.quantity-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string-spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.impl.search-param.quantity :as spq]
    [blaze.db.impl.search-param.quantity-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest resource-keys-test
  (testing "non matching op"
    ;; although a non-matching op isn't allowed in the spec, it could happen at
    ;; runtime, and we have to test that case
    (st/unstrument `spq/resource-keys!)

    (try
      (spq/resource-keys! {} (codec/c-hash "value-quantity") 0 0 {:op :foo})
      (catch Exception e
        (is (= "No matching clause: :foo" (ex-message e)))))

    (testing "with start-id"
      (try
        (spq/resource-keys! {} (codec/c-hash "value-quantity") 0 0 {:op :foo} 0)
        (catch Exception e
          (is (= "No matching clause: :foo" (ex-message e))))))))


(defn value-quantity-param [search-param-registry]
  (sr/get search-param-registry "value-quantity" "Observation"))


(deftest value-quantity-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (given (value-quantity-param search-param-registry)
      :name := "value-quantity"
      :code := "value-quantity"
      :c-hash := (codec/c-hash "value-quantity"))))


(deftest matches-test
  (testing "non matching op"
    ;; although a non-matching op isn't allowed in the spec, it could happen at
    ;; runtime, and we have to test that case
    (st/unstrument `spq/matches?)

    (try
      (spq/matches? {} (codec/c-hash "value-quantity") nil 0 {:op :foo})
      (catch Exception e
        (is (= "No matching clause: :foo" (ex-message e)))))))


(defn compile-quantity-value [search-param-registry value]
  (-> (value-quantity-param search-param-registry)
      (search-param/compile-values nil [value])
      (first)))


(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "eq"
      (are [value lower-bound upper-bound]
        (given (compile-quantity-value search-param-registry value)
          :op := :eq
          :lower-bound := lower-bound
          :upper-bound := upper-bound)

        "23.4" (codec/quantity nil 23.35M) (codec/quantity nil 23.45M)
        "23.0|kg/m2" (codec/quantity "kg/m2" 22.95M) (codec/quantity "kg/m2" 23.05M)
        ;; with white space between value and unit
        "23.0| kg/m2" (codec/quantity "kg/m2" 22.95M) (codec/quantity "kg/m2" 23.05M)
        "23.0 | kg/m2" (codec/quantity "kg/m2" 22.95M) (codec/quantity "kg/m2" 23.05M)
        "0.1" (codec/quantity nil 0.05M) (codec/quantity nil 0.15M)
        "0" (codec/quantity nil -0.5M) (codec/quantity nil 0.5M)
        "0.0" (codec/quantity nil -0.05M) (codec/quantity nil 0.05M)))

    (testing "gt lt ge le"
      (doseq [op [:gt :lt :ge :le]]
        (are [value exact-value]
          (given (compile-quantity-value search-param-registry value)
            :op := op
            :exact-value := exact-value)

          (str (name op) "23.4") (codec/quantity nil 23.4M)
          (str (name op) "23.0|kg/m2") (codec/quantity "kg/m2" 23.0M)
          ;; with white space between value and unit
          (str (name op) "23.0| kg/m2") (codec/quantity "kg/m2" 23.0M)
          (str (name op) "23.0 | kg/m2") (codec/quantity "kg/m2" 23.0M)
          (str (name op) "0.1") (codec/quantity nil 0.1M)
          ;; with white space between op and value
          (str (name op) " 1") (codec/quantity nil 1M))))

    (testing "invalid decimal value"
      (given (search-param/compile-values
               (value-quantity-param search-param-registry) nil ["a"])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid decimal value `a` in search parameter `value-quantity`."))

    (testing "unsupported prefix"
      (given (search-param/compile-values
               (value-quantity-param search-param-registry) nil ["ne23"])
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported prefix `ne` in search parameter `value-quantity`."))))


(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "Observation value-quantity"
      (testing "with value, system and code"
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-155558"
               :status #fhir/code"final"
               :value
               #fhir/Quantity
                       {:value 140M
                        :code #fhir/code"mm[Hg]"
                        :system #fhir/uri"http://unitsofmeasure.org"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (search-param/index-entries
                (sr/get search-param-registry "value-quantity" "Observation")
                [] hash observation)]

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity nil 140M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity nil 140M)))

          (testing "second SearchParamValueResource key is about `code value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity "mm[Hg]" 140M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `code value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity "mm[Hg]" 140M)))

          (testing "third SearchParamValueResource key is about `system|code value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k4))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity "http://unitsofmeasure.org|mm[Hg]" 140M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "third ResourceSearchParamValue key is about `system|code value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity "http://unitsofmeasure.org|mm[Hg]" 140M)))))

      (testing "with value and unit"
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-155558"
               :status #fhir/code"final"
               :value
               #fhir/Quantity
                       {:value 140M
                        :unit "mmHg"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3]]
              (search-param/index-entries
                (sr/get search-param-registry "value-quantity" "Observation")
                [] hash observation)]

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity nil 140M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity nil 140M)))

          (testing "second SearchParamValueResource key is about `unit value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity "mmHg" 140M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `unit value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity "mmHg" 140M)))))

      (testing "with value, unit and code where unit equals code"
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-155558"
               :status #fhir/code"final"
               :value
               #fhir/Quantity
                       {:value 120M
                        :unit "mm[Hg]"
                        :code #fhir/code"mm[Hg]"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3]]
              (search-param/index-entries
                (sr/get search-param-registry "value-quantity" "Observation")
                [] hash observation)]

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity nil 120M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity nil 120M)))

          (testing "second SearchParamValueResource key is about `code value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity "mm[Hg]" 120M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `code value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity "mm[Hg]" 120M)))))

      (testing "with value, unit and code where unit differs from code"
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-155558"
               :status #fhir/code"final"
               :value
               #fhir/Quantity
                       {:value 120M
                        :unit "mmHg"
                        :code #fhir/code"mm[Hg]"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (search-param/index-entries
                (sr/get search-param-registry "value-quantity" "Observation")
                [] hash observation)]

          (testing "first SearchParamValueResource key is about `value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity nil 120M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "first ResourceSearchParamValue key is about `value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity nil 120M)))

          (testing "second SearchParamValueResource key is about `code value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k2))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity "mm[Hg]" 120M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "second ResourceSearchParamValue key is about `code value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity "mm[Hg]" 120M)))

          (testing "third SearchParamValueResource key is about `unit value`"
            (given (sp-vr-tu/decode-key-human (bb/wrap k4))
              :code := "value-quantity"
              :type := "Observation"
              :v-hash := (codec/quantity "mmHg" 120M)
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "third ResourceSearchParamValue key is about `unit value`"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
              :code := "value-quantity"
              :v-hash := (codec/quantity "mmHg" 120M))))))

    (testing "FHIRPath evaluation problem"
      (let [resource {:fhir/type :fhir/Observation :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                   (sr/get search-param-registry "value-quantity" "Observation")
                   [] hash resource)
            ::anom/category := ::anom/fault))))

    (testing "skip warning"
      (is (nil? (spq/index-entries "" nil))))))

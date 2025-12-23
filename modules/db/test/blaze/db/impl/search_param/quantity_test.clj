(ns blaze.db.impl.search-param.quantity-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.quantity :as spq]
   [blaze.db.impl.search-param.quantity-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
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

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest resource-keys-test
  (testing "non matching op"
    ;; although a non-matching op isn't allowed in the spec, it could happen at
    ;; runtime, and we have to test that case
    (st/unstrument `spq/index-handles)

    (try
      (spq/index-handles {} (codec/c-hash "value-quantity") 0 0 {:op :foo})
      (catch Exception e
        (is (= "No matching clause: :foo" (ex-message e)))))

    (testing "with start-id"
      (try
        (spq/index-handles {} (codec/c-hash "value-quantity") 0 0 {:op :foo} 0)
        (catch Exception e
          (is (= "No matching clause: :foo" (ex-message e))))))))

(defn value-quantity-param [search-param-registry]
  (sr/get search-param-registry "value-quantity" "Observation"))

(deftest value-quantity-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (value-quantity-param search-param-registry)
      :name := "value-quantity"
      :code := "value-quantity"
      :c-hash := (codec/c-hash "value-quantity"))))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "unknown modifier"
      (given (search-param/validate-modifier
              (value-quantity-param search-param-registry) "unknown")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown modifier `unknown` on search parameter `value-quantity`."))

    (testing "modifier not implemented"
      (given (search-param/validate-modifier
              (value-quantity-param search-param-registry) "missing")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported modifier `missing` on search parameter `value-quantity`."))))

(defn compile-quantity-value [search-param-registry value]
  (-> (value-quantity-param search-param-registry)
      (search-param/compile-values nil [value])
      (first)))

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "eq"
      (given (compile-quantity-value search-param-registry "23.4")
        :op := :eq
        :lower-bound := (codec/quantity nil 23.35M)
        :upper-bound := (codec/quantity nil 23.45M))

      (given (compile-quantity-value search-param-registry "23.0|kg/m2")
        :op := :eq
        :lower-bound := (codec/quantity "kg/m2" 22.95M)
        :upper-bound := (codec/quantity "kg/m2" 23.05M))

      ;; with white space between value and unit
      (given (compile-quantity-value search-param-registry "23.0| kg/m2")
        :op := :eq
        :lower-bound := (codec/quantity "kg/m2" 22.95M)
        :upper-bound := (codec/quantity "kg/m2" 23.05M))

      (given (compile-quantity-value search-param-registry "23.0 | kg/m2")
        :op := :eq
        :lower-bound := (codec/quantity "kg/m2" 22.95M)
        :upper-bound := (codec/quantity "kg/m2" 23.05M))

      (given (compile-quantity-value search-param-registry "0.1")
        :op := :eq
        :lower-bound := (codec/quantity nil 0.05M)
        :upper-bound := (codec/quantity nil 0.15M))

      (given (compile-quantity-value search-param-registry "0")
        :op := :eq
        :lower-bound := (codec/quantity nil -0.5M)
        :upper-bound := (codec/quantity nil 0.5M))

      (given (compile-quantity-value search-param-registry "0.0")
        :op := :eq
        :lower-bound := (codec/quantity nil -0.05M)
        :upper-bound := (codec/quantity nil 0.05M)))

    (testing "gt lt ge le"
      (doseq [op [:gt :lt :ge :le]]
        (given (compile-quantity-value search-param-registry (str (name op) "23.4"))
          :op := op
          :exact-value := (codec/quantity nil 23.4M))

        (given (compile-quantity-value search-param-registry (str (name op) "23.0|kg/m2"))
          :op := op
          :exact-value := (codec/quantity "kg/m2" 23.0M))

        ;; with white space between value and unit
        (given (compile-quantity-value search-param-registry (str (name op) "23.0| kg/m2"))
          :op := op
          :exact-value := (codec/quantity "kg/m2" 23.0M))

        (given (compile-quantity-value search-param-registry (str (name op) "23.0 | kg/m2"))
          :op := op
          :exact-value := (codec/quantity "kg/m2" 23.0M))

        (given (compile-quantity-value search-param-registry (str (name op) "0.1"))
          :op := op
          :exact-value := (codec/quantity nil 0.1M))

        ;; with white space between op and value
        (given (compile-quantity-value search-param-registry (str (name op) " 1"))
          :op := op
          :exact-value := (codec/quantity nil 1M))))

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

(deftest estimated-scan-size-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (value-quantity-param search-param-registry)]
      (is (ba/unsupported? (p/-estimated-scan-size search-param nil nil nil nil))))))

(deftest ordered-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (value-quantity-param search-param-registry)]
      (is (false? (p/-supports-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil nil))))))

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (value-quantity-param search-param-registry)]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil))))))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Observation value-quantity"
      (testing "with value, system and code"
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-155558"
               :status #fhir/code "final"
               :value
               #fhir/Quantity
                {:value #fhir/decimal 140M
                 :code #fhir/code "mm[Hg]"
                 :system #fhir/uri "http://unitsofmeasure.org"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (index-entries
               (value-quantity-param search-param-registry)
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
               :status #fhir/code "final"
               :value
               #fhir/Quantity
                {:value #fhir/decimal 140M
                 :unit #fhir/string "mmHg"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3]]
              (index-entries
               (value-quantity-param search-param-registry)
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
               :status #fhir/code "final"
               :value
               #fhir/Quantity
                {:value #fhir/decimal 120M
                 :unit #fhir/string "mm[Hg]"
                 :code #fhir/code "mm[Hg]"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3]]
              (index-entries
               (value-quantity-param search-param-registry)
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
               :status #fhir/code "final"
               :value
               #fhir/Quantity
                {:value #fhir/decimal 120M
                 :unit #fhir/string "mmHg"
                 :code #fhir/code "mm[Hg]"}}
              hash (hash/generate observation)
              [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
              (index-entries
               (value-quantity-param search-param-registry)
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
              :v-hash := (codec/quantity "mmHg" 120M)))))

      (testing "without Quantity value"
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-155558"
               :status #fhir/code "final"
               :value
               #fhir/Quantity
                {:code #fhir/code "mm[Hg]"
                 :system #fhir/uri "http://unitsofmeasure.org"}}
              hash (hash/generate observation)]

          (is (empty? (index-entries
                       (value-quantity-param search-param-registry)
                       [] hash observation)))))

      (testing "without value"
        (let [observation
              {:fhir/type :fhir/Observation
               :id "id-155558"
               :status #fhir/code "final"}
              hash (hash/generate observation)]

          (is (empty? (index-entries
                       (value-quantity-param search-param-registry)
                       [] hash observation))))))

    (testing "FHIRPath evaluation problem"
      (let [resource {:fhir/type :fhir/Observation :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                  (value-quantity-param search-param-registry)
                  [] hash resource)
            ::anom/category := ::anom/fault))))

    (testing "skip warning"
      (is (nil? (spq/index-entries "" nil))))))

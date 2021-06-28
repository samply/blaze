(ns blaze.db.impl.search-param.composite-test
  (:require
    [blaze.byte-string :as bs]
    [blaze.byte-string-spec]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.hash-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def code-value-quantity-param
  (sr/get search-param-registry "code-value-quantity" "Observation"))


(def code-value-concept-param
  (sr/get search-param-registry "code-value-concept" "Observation"))


(deftest name-test
  (is (= "code-value-quantity" (:name code-value-quantity-param))))


(deftest code-test
  (is (= "code-value-quantity" (:code code-value-quantity-param))))


(deftest c-hash-test
  (is (= (codec/c-hash "code-value-quantity") (:c-hash code-value-quantity-param))))


(defn- split-value [bs]
  [(bs/subs bs 0 4) (bs/subs bs 4)])


(defn compile-code-quantity-value [value]
  (first (search-param/compile-values code-value-quantity-param nil [value])))


(deftest compile-value-test
  (testing "eq"
    (are [value lower-bound upper-bound]
      (given (compile-code-quantity-value value)
        :op := :eq
        :lower-bound := lower-bound
        :upper-bound := upper-bound)

      "8480-6$23.4"
      (bs/concat (codec/v-hash "8480-6") (codec/quantity nil 23.35M))
      (bs/concat (codec/v-hash "8480-6") (codec/quantity nil 23.45M))))

  (testing "ge"
    (are [value exact-value]
      (given (compile-code-quantity-value value)
        :op := :ge
        :exact-value := exact-value)

      "8480-6$ge23|kg/m2"
      (bs/concat (codec/v-hash "8480-6") (codec/quantity "kg/m2" 23.00M))))

  (testing "invalid quantity decimal value"
    (given (search-param/compile-values code-value-quantity-param nil ["a$a"])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid decimal value `a` in search parameter `code-value-quantity`."))

  (testing "unsupported quantity prefix"
    (given (search-param/compile-values code-value-quantity-param nil ["a$ne1"])
      ::anom/category := ::anom/unsupported
      ::anom/message := "Unsupported prefix `ne` in search parameter `code-value-quantity`.")))


(def ^:private observation-code
  (codec/v-hash "8480-6"))


(def ^:private observation-system
  (codec/v-hash "http://loinc.org|"))


(def ^:private observation-system-code
  (codec/v-hash "http://loinc.org|8480-6"))


(def ^:private value
  (codec/quantity nil 100M))


(def ^:private value-code
  (codec/quantity "mm[Hg]" 100M))


(def ^:private value-system-code
  (codec/quantity "http://unitsofmeasure.org|mm[Hg]" 100M))


(deftest index-entries-test
  (testing "Observation code-value-quantity"
    (let [observation
          {:fhir/type :fhir/Observation
           :id "id-155558"
           :status #fhir/code"final"
           :code
           #fhir/CodeableConcept
               {:coding
                [#fhir/Coding
                    {:system #fhir/uri"http://loinc.org"
                     :code #fhir/code"8480-6"}]}
           :value
           #fhir/Quantity
               {:value 100M
                :code #fhir/code"mm[Hg]"
                :system #fhir/uri"http://unitsofmeasure.org"}}
          hash (hash/generate observation)
          [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]
           [_ k6] [_ k7] [_ k8] [_ k9] [_ k10] [_ k11]
           [_ k12] [_ k13] [_ k14] [_ k15] [_ k16] [_ k17]]
          (search-param/index-entries code-value-quantity-param
                                      [] hash observation)]

      (testing "`code` followed by `value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "code-value-quantity"
            :type := "Observation"
            [:v-hash split-value 0] := observation-code
            [:v-hash split-value 1] := value
            :id := "id-155558"
            :hash-prefix := (codec/hash-prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "Observation"
            :id := "id-155558"
            :hash-prefix := (codec/hash-prefix hash)
            :code := "code-value-quantity"
            [:v-hash split-value 0] := observation-code
            [:v-hash split-value 1] := value)))

      (testing "`code` followed by `code value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k2))
            [:v-hash split-value 0] := observation-code
            [:v-hash split-value 1] := value-code))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k3))
            [:v-hash split-value 0] := observation-code
            [:v-hash split-value 1] := value-code)))

      (testing "`code` followed by `system|code value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k4))
            [:v-hash split-value 0] := observation-code
            [:v-hash split-value 1] := value-system-code))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k5))
            [:v-hash split-value 0] := observation-code
            [:v-hash split-value 1] := value-system-code)))

      (testing "`system|` followed by `value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k6))
            [:v-hash split-value 0] := observation-system
            [:v-hash split-value 1] := value))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k7))
            [:v-hash split-value 0] := observation-system
            [:v-hash split-value 1] := value)))

      (testing "`system|` followed by `code value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k8))
            [:v-hash split-value 0] := observation-system
            [:v-hash split-value 1] := value-code))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k9))
            [:v-hash split-value 0] := observation-system
            [:v-hash split-value 1] := value-code)))

      (testing "`system|` followed by `system|code value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k10))
            [:v-hash split-value 0] := observation-system
            [:v-hash split-value 1] := value-system-code))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k11))
            [:v-hash split-value 0] := observation-system
            [:v-hash split-value 1] := value-system-code)))

      (testing "`system|code` followed by `value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k12))
            [:v-hash split-value 0] := observation-system-code
            [:v-hash split-value 1] := value))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k13))
            [:v-hash split-value 0] := observation-system-code
            [:v-hash split-value 1] := value)))

      (testing "`system|code` followed by `code value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k14))
            [:v-hash split-value 0] := observation-system-code
            [:v-hash split-value 1] := value-code))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k15))
            [:v-hash split-value 0] := observation-system-code
            [:v-hash split-value 1] := value-code)))

      (testing "`system|code` followed by `system|code value`"
        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k16))
            [:v-hash split-value 0] := observation-system-code
            [:v-hash split-value 1] := value-system-code))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k17))
            [:v-hash split-value 0] := observation-system-code
            [:v-hash split-value 1] := value-system-code)))))

  (testing "FHIRPath evaluation problem"
    (testing "code-value-quantity"
      (let [resource {:fhir/type :fhir/Observation :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                   code-value-quantity-param
                   [] hash resource)
            ::anom/category := ::anom/fault))))

    (testing "code-value-concept"
      (let [resource {:fhir/type :fhir/Observation :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                   code-value-concept-param
                   [] hash resource)
            ::anom/category := ::anom/fault))))))


(deftest create-test
  (testing "not found component"
    (given (sr/search-param
             {}
             {:type "composite"
              :component
              [{:definition "url-210148"}]})
      ::anom/category := ::anom/unsupported
      :url := "url-210148"))

  (testing "FHIRPath compilation error"
    (with-redefs
      [fhir-path/compile
       (fn [_]
         {::anom/category ::anom/fault})]
      (given (sr/search-param
               {"url-210148"
                {:type "token"}
                "url-211659"
                {:type "token"}}
               {:type "composite"
                :component
                [{:definition "url-210148"
                  :expression "expr-211649"}
                 {:definition "url-211659"}]})
        ::anom/category := ::anom/unsupported
        :expression := "expr-211649"))))

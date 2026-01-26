(ns blaze.db.impl.search-param.composite-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-buffer :as bb]
   [blaze.byte-string :as bs]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
   [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.core :as sc]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [blaze.fhir_path GetChildrenExpression TypedStartExpression]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn code-value-quantity-param [search-param-registry]
  (sr/get search-param-registry "code-value-quantity" "Observation"))

(defn code-value-concept-param [search-param-registry]
  (sr/get search-param-registry "code-value-concept" "Observation"))

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}
   ::ts/not-available {}})

(deftest code-value-quantity-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (code-value-quantity-param search-param-registry)
      :name := "code-value-quantity"
      :code := "code-value-quantity"
      :c-hash := (codec/c-hash "code-value-quantity"))))

(defn- split-value [bs]
  [(bs/subs bs 0 4) (bs/subs bs 4)])

(defn compile-code-quantity-value [search-param-registry value]
  (-> (code-value-quantity-param search-param-registry)
      (search-param/compile-values nil [value])
      (first)))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "unknown modifier"
      (given (search-param/validate-modifier
              (code-value-quantity-param search-param-registry) "unknown")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown modifier `unknown` on search parameter `code-value-quantity`."))))

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "eq"
      (given (compile-code-quantity-value search-param-registry "8480-6$23.4")
        :op := :eq
        :lower-bound := (bs/concat (codec/v-hash "8480-6") (codec/quantity nil 23.35M))
        :upper-bound := (bs/concat (codec/v-hash "8480-6") (codec/quantity nil 23.45M))))

    (testing "ge"
      (given (compile-code-quantity-value search-param-registry "8480-6$ge23|kg/m2")
        :op := :ge
        :exact-value := (bs/concat (codec/v-hash "8480-6") (codec/quantity "kg/m2" 23.00M))))

    (testing "invalid quantity decimal value"
      (given (search-param/compile-values
              (code-value-quantity-param search-param-registry) nil ["a$a"])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid decimal value `a` in search parameter `code-value-quantity`."))

    (testing "unsupported quantity prefix"
      (given (search-param/compile-values
              (code-value-quantity-param search-param-registry) nil ["a$ne1"])
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported prefix `ne` in search parameter `code-value-quantity`."))))

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

(defn anom-vec [coll]
  (transduce (halt-when ba/anomaly?) conj coll))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "Observation code-value-quantity"
      (let [observation
            {:fhir/type :fhir/Observation :id "id-155558"
             :status #fhir/code "final"
             :code
             #fhir/CodeableConcept
              {:coding
               [#fhir/Coding
                 {:system #fhir/uri "http://loinc.org"
                  :code #fhir/code "8480-6"}]}
             :value
             #fhir/Quantity
              {:value #fhir/decimal 100M
               :code #fhir/code "mm[Hg]"
               :system #fhir/uri "http://unitsofmeasure.org"}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]
             [_ k6] [_ k7] [_ k8] [_ k9] [_ k10] [_ k11]
             [_ k12] [_ k13] [_ k14] [_ k15] [_ k16] [_ k17]]
            (index-entries
             (code-value-quantity-param search-param-registry)
             [] hash observation)]

        (testing "`code` followed by `value`"
          (testing "SearchParamValueResource key"
            (given (sp-vr-tu/decode-key-human (bb/wrap k0))
              :code := "code-value-quantity"
              :type := "Observation"
              [:v-hash split-value 0] := observation-code
              [:v-hash split-value 1] := value
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)))

          (testing "ResourceSearchParamValue key"
            (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
              :type := "Observation"
              :id := "id-155558"
              :hash-prefix := (hash/prefix hash)
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

          (testing "on main-value"
            (with-redefs [fhir-path/eval
                          (fn [_ _ _]
                            {::anom/category ::anom/fault
                             ::x ::y})]
              (given (search-param/index-entries
                      (code-value-quantity-param search-param-registry)
                      [] hash resource)
                ::anom/category := ::anom/fault
                ::x := ::y)))

          (testing "on first component"
            (with-redefs [fhir-path/eval
                          (fn [_ expr value]
                            (if (instance? TypedStartExpression expr)
                              [value]
                              {::anom/category ::anom/fault
                               ::x ::y}))]
              (given (anom-vec (search-param/index-entries
                                (code-value-quantity-param search-param-registry)
                                [] hash resource))
                ::anom/category := ::anom/fault
                ::x := ::y)))

          (testing "on second component"
            (with-redefs [fhir-path/eval
                          (fn [_ expr value]
                            (cond
                              (instance? TypedStartExpression expr)
                              [value]
                              (instance? GetChildrenExpression expr)
                              [#fhir/CodeableConcept
                                {:coding
                                 [#fhir/Coding
                                   {:system #fhir/uri "system-204435"
                                    :code #fhir/code "code-204441"}]}]
                              :else
                              {::anom/category ::anom/fault
                               ::x ::y}))]
              (given (anom-vec (search-param/index-entries
                                (code-value-quantity-param search-param-registry)
                                [] hash resource))
                ::anom/category := ::anom/fault
                ::x := ::y)))))

      (testing "code-value-concept"
        (let [resource {:fhir/type :fhir/Observation :id "foo"}
              hash (hash/generate resource)]

          (testing "on main-value"
            (with-redefs [fhir-path/eval
                          (fn [_ _ _]
                            {::anom/category ::anom/fault
                             ::x ::y})]
              (given (search-param/index-entries
                      (code-value-concept-param search-param-registry)
                      [] hash resource)
                ::anom/category := ::anom/fault
                ::x := ::y))))))))

(deftest create-test
  (testing "not found component"
    (given (sc/search-param
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
      (given (sc/search-param
              {:index
               {"url-210148"
                {:type "token"}
                "url-211659"
                {:type "token"}}}
              {:type "composite"
               :component
               [{:definition "url-210148"
                 :expression "expr-211649"}
                {:definition "url-211659"}]})
        ::anom/category := ::anom/unsupported
        :expression := "expr-211649"))))

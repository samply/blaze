(ns blaze.db.impl.search-param.number-test
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
   [blaze.db.impl.search-param.number :as spn]
   [blaze.db.impl.search-param.util-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn probability-param [search-param-registry]
  (sr/get search-param-registry "probability" "RiskAssessment"))

(defn compile-number-value [search-param-registry value]
  (-> (probability-param search-param-registry)
      (search-param/compile-values nil [value])
      (first)))

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "eq"
      (given (compile-number-value search-param-registry "23.4")
        :op := :eq
        :lower-bound := (codec/number 23.35M)
        :upper-bound := (codec/number 23.45M))

      (given (compile-number-value search-param-registry "0.1")
        :op := :eq
        :lower-bound := (codec/number 0.05M)
        :upper-bound := (codec/number 0.15M))

      (given (compile-number-value search-param-registry "0")
        :op := :eq
        :lower-bound := (codec/number -0.5M)
        :upper-bound := (codec/number 0.5M))

      (given (compile-number-value search-param-registry "0.0")
        :op := :eq
        :lower-bound := (codec/number -0.05M)
        :upper-bound := (codec/number 0.05M)))

    (testing "gt lt ge le"
      (doseq [op [:gt :lt :ge :le]]
        (given (compile-number-value search-param-registry (str (name op) "23"))
          :op := op
          :exact-value := (codec/number 23M))

        (given (compile-number-value search-param-registry (str (name op) "0.1"))
          :op := op
          :exact-value := (codec/number 0.1M))))

    (testing "invalid decimal value"
      (given (search-param/compile-values
              (probability-param search-param-registry) nil ["a"])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid decimal value `a` in search parameter `probability`."))

    (testing "unsupported prefix"
      (given (search-param/compile-values
              (probability-param search-param-registry) nil ["ne23"])
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported prefix `ne` in search parameter `probability`."))))

(deftest estimated-scan-size-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (probability-param search-param-registry)]
      (is (ba/unsupported? (p/-estimated-scan-size search-param nil nil nil nil))))))

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (probability-param search-param-registry)]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil))))))

(defn- index-entries [search-param linked-compartments hash resource]
  (vec (search-param/index-entries search-param linked-compartments hash resource)))

(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "ResearchStudy recruitment-actual"
      (let [resource {:fhir/type :fhir/ResearchStudy :id "id-102236"
                      :recruitment
                      {:fhir/type :fhir.ResearchStudy/recruitment
                       :actualNumber #fhir/unsignedInt 102229}}
            hash (hash/generate resource)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "recruitment-actual" "ResearchStudy")
             [] hash resource)]

        (testing "SearchParamValueResource key"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "recruitment-actual"
            :type := "ResearchStudy"
            :v-hash := (codec/number (BigDecimal/valueOf 102229))
            :id := "id-102236"
            :hash-prefix := (hash/prefix hash)))

        (testing "ResourceSearchParamValue key"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "ResearchStudy"
            :id := "id-102236"
            :hash-prefix := (hash/prefix hash)
            :code := "recruitment-actual"
            :v-hash := (codec/number (BigDecimal/valueOf 102229))))))

    (testing "FHIRPath evaluation problem"
      (let [resource {:fhir/type :fhir/RiskAssessment :id "foo"}
            hash (hash/generate resource)]

        (with-redefs [fhir-path/eval (fn [_ _ _] {::anom/category ::anom/fault})]
          (given (search-param/index-entries
                  (sr/get search-param-registry "probability" "RiskAssessment")
                  [] hash resource)
            ::anom/category := ::anom/fault)))))

  (testing "skip warning"
    (is (nil? (spn/index-entries "" nil)))))

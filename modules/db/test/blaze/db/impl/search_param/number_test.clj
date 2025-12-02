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

(defn probability-param [search-param-registry]
  (sr/get search-param-registry "probability" "RiskAssessment"))

(defn compile-number-value [search-param-registry value]
  (-> (probability-param search-param-registry)
      (search-param/compile-values nil [value])
      (first)))

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "unknown modifier"
      (given (search-param/validate-modifier
              (probability-param search-param-registry) "unknown")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown modifier `unknown` on search parameter `probability`."))

    (testing "modifier not implemented"
      (given (search-param/validate-modifier
              (probability-param search-param-registry) "missing")
        ::anom/category := ::anom/unsupported
        ::anom/message := "Unsupported modifier `missing` on search parameter `probability`."))))

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

(deftest ordered-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (probability-param search-param-registry)]
      (is (false? (p/-supports-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil nil))))))

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
    (testing "RiskAssessment probability"
      (let [risk-assessment
            {:fhir/type :fhir/RiskAssessment
             :id "id-163630"
             :prediction
             [{:fhir/type :fhir.RiskAssessment/prediction
               :probability #fhir/decimal 0.9M}]}
            hash (hash/generate risk-assessment)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "probability" "RiskAssessment")
             [] hash risk-assessment)]

        (testing "first SearchParamValueResource key is about `value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "probability"
            :type := "RiskAssessment"
            :v-hash := (codec/number 0.9M)
            :id := "id-163630"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "RiskAssessment"
            :id := "id-163630"
            :hash-prefix := (hash/prefix hash)
            :code := "probability"
            :v-hash := (codec/number 0.9M)))))

    (testing "MolecularSequence variant-start"
      (let [risk-assessment
            {:fhir/type :fhir/MolecularSequence
             :id "id-170736"
             :variant
             [{:fhir/type :fhir.MolecularSequence/variant
               :start #fhir/integer 1}]}
            hash (hash/generate risk-assessment)
            [[_ k0] [_ k1]]
            (index-entries
             (sr/get search-param-registry "variant-start" "MolecularSequence")
             [] hash risk-assessment)]

        (testing "first SearchParamValueResource key is about `value`"
          (given (sp-vr-tu/decode-key-human (bb/wrap k0))
            :code := "variant-start"
            :type := "MolecularSequence"
            :v-hash := (codec/number 1M)
            :id := "id-170736"
            :hash-prefix := (hash/prefix hash)))

        (testing "first ResourceSearchParamValue key is about `value`"
          (given (r-sp-v-tu/decode-key-human (bb/wrap k1))
            :type := "MolecularSequence"
            :id := "id-170736"
            :hash-prefix := (hash/prefix hash)
            :code := "variant-start"
            :v-hash := (codec/number 1M)))))

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

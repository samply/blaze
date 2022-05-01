(ns blaze.db.impl.search-param.number-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string-spec]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-search-param-value-test-util :as r-sp-v-tu]
    [blaze.db.impl.index.search-param-value-resource-test-util :as sp-vr-tu]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.impl.search-param.number :as spn]
    [blaze.db.impl.search-param.util-spec]
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


(defn probability-param [search-param-registry]
  (sr/get search-param-registry "probability" "RiskAssessment"))


(defn compile-number-value [search-param-registry value]
  (-> (probability-param search-param-registry)
      (search-param/compile-values nil [value])
      (first)))


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest compile-value-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "eq"
      (are [value lower-bound upper-bound]
        (given (compile-number-value search-param-registry value)
          :op := :eq
          :lower-bound := lower-bound
          :upper-bound := upper-bound)

        "23.4" (codec/number 23.35M) (codec/number 23.45M)
        "0.1" (codec/number 0.05M) (codec/number 0.15M)
        "0" (codec/number -0.5M) (codec/number 0.5M)
        "0.0" (codec/number -0.05M) (codec/number 0.05M)))

    (testing "gt lt ge le"
      (doseq [op [:gt :lt :ge :le]]
        (are [value exact-value]
          (given (compile-number-value search-param-registry value)
            :op := op
            :exact-value := exact-value)

          (str (name op) "23") (codec/number 23M)
          (str (name op) "0.1") (codec/number 0.1M))))

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


(deftest index-entries-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (testing "RiskAssessment probability"
      (let [risk-assessment
            {:fhir/type :fhir/RiskAssessment
             :id "id-163630"
             :prediction
             [{:fhir/type :fhir.RiskAssessment/prediction
               :probability 0.9M}]}
            hash (hash/generate risk-assessment)
            [[_ k0] [_ k1]]
            (search-param/index-entries
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
            (search-param/index-entries
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

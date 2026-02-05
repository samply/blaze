(ns blaze.elm.expression.cache.bloom-filter-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.test-util :as ctu]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [blaze.elm.expression.cache.codec-spec]
   [blaze.elm.expression.cache.codec.by-t-spec]
   [blaze.elm.expression.cache.codec.form-spec]
   [blaze.elm.literal]
   [blaze.fhir.test-util]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(ctu/instrument-compile)
(log/set-min-level! :trace)

(defn- fixture [f]
  (st/instrument)
  (ctu/instrument-compile)
  (log/set-min-level! :trace)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(deftest create-test
  (testing "with empty database"
    (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
      (let [elm #elm/exists #elm/retrieve{:type "Observation"}
            expr (c/compile {:node node :eval-context "Patient"} elm)]

        (given (bloom-filter/create node expr)
          ::bloom-filter/t := 0
          ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
          ::bloom-filter/patient-count := 0
          ::bloom-filter/mem-size := 11981))))

  (testing "with one Patient with one Observation"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [elm #elm/exists #elm/retrieve{:type "Observation"}
            expr (c/compile {:node node :eval-context "Patient"} elm)]

        (given (bloom-filter/create node expr)
          ::bloom-filter/t := 1
          ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
          ::bloom-filter/patient-count := 1
          ::bloom-filter/mem-size := 11981))))

  (testing "with two Patients on of which has one Observation"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Patient :id "1"}]]]

      (let [elm #elm/exists #elm/retrieve{:type "Observation"}
            expr (c/compile {:node node :eval-context "Patient"} elm)]

        (given (bloom-filter/create node expr)
          ::bloom-filter/t := 1
          ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
          ::bloom-filter/patient-count := 1
          ::bloom-filter/mem-size := 11981)))))

(deftest recreate-test
  (testing "with empty database"
    (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
      (let [elm #elm/exists #elm/retrieve{:type "Observation"}
            expr (c/compile {:node node :eval-context "Patient"} elm)
            bloom-filter (bloom-filter/create node expr)]

        (given (bloom-filter/recreate node bloom-filter expr)
          ::bloom-filter/t := 0
          ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
          ::bloom-filter/patient-count := 0
          ::bloom-filter/mem-size := 11981))))

  (testing "with one Patient with one Observation added"
    (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
      (let [elm #elm/exists #elm/retrieve{:type "Observation"}
            expr (c/compile {:node node :eval-context "Patient"} elm)
            bloom-filter (bloom-filter/create node expr)]

        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "0"}]
                           [:put {:fhir/type :fhir/Observation :id "0"
                                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]])

        (given (bloom-filter/recreate node bloom-filter expr)
          ::bloom-filter/t := 1
          ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
          ::bloom-filter/patient-count := 1
          ::bloom-filter/mem-size := 11981))))

  (testing "with one additional Patient with one Observation added"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [elm #elm/exists #elm/retrieve{:type "Observation"}
            expr (c/compile {:node node :eval-context "Patient"} elm)
            bloom-filter (bloom-filter/create node expr)]

        @(d/transact node [[:put {:fhir/type :fhir/Patient :id "1"}]
                           [:put {:fhir/type :fhir/Observation :id "1"
                                  :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]])

        (given (bloom-filter/recreate node bloom-filter expr)
          ::bloom-filter/t := 2
          ::bloom-filter/expr-form := "(exists (retrieve \"Observation\"))"
          ::bloom-filter/patient-count := 1
          ::bloom-filter/mem-size := 11981)))))

(ns blaze.fhir.operation.evaluate-measure.cql-test
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.anomaly-spec]
   [blaze.cql.translator :as cql-translator]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.compiler.library-spec]
   [blaze.elm.expression :as expr]
   [blaze.fhir.operation.evaluate-measure.cql :as cql]
   [blaze.fhir.operation.evaluate-measure.cql-spec]
   [blaze.fhir.operation.evaluate-measure.test-util :as em-tu]
   [blaze.fhir.spec.type]
   [blaze.module.test-util :refer [given-failed-future with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service-spec]
   [blaze.terminology-service.local :as ts-local]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def library-empty
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'")

(def library-gender
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    Patient.gender = 'male'

  define Gender:
    Patient.gender")

(def library-error
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  parameter Numbers List<Integer>

  context Patient

  define InInitialPopulation:
    singleton from Numbers")

(def library-encounter
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    [Encounter]")

(def library-specimen
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Specimen

  define InInitialPopulation:
    true")

(defn- compile-library [{:blaze.db/keys [node] ::ts/keys [local]} cql]
  (when-ok [library (cql-translator/translate cql)]
    (library/compile-library {:node node :terminology-service local} library {})))

(defn- failing-eval [msg]
  (fn [_ _ _] (throw (Exception. ^String msg))))

(defn- context
  [{:blaze.db/keys [node]
    ::expr/keys [cache]
    :blaze.test/keys [fixed-clock executor]
    :as system}
   library]
  (let [{:keys [expression-defs function-defs]} (compile-library system library)]
    {:db (d/db node)
     :now (time/offset-date-time fixed-clock)
     ::expr/cache cache
     :interrupted? (constantly nil)
     :expression-defs expression-defs
     :function-defs function-defs
     :executor executor}))

(def ^:private config
  (assoc
   mem-node-config
   ::expr/cache
   {:node (ig/ref :blaze.db/node)
    :executor (ig/ref :blaze.test/executor)}
   ::ts/local
   {:node (ig/ref :blaze.db/node)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :graph-cache (ig/ref ::ts-local/graph-cache)}
   :blaze.test/executor {}
   :blaze.test/fixed-rng-fn {}
   ::ts-local/graph-cache {}))

(def ^:private conj-reduce-op
  (fn [_db] conj))

(def ^:private count-reduce-op
  (fn [_db] ((map (constantly 1)) +)))

(defn with-ops [context reduce-op combine-op]
  (assoc context :reduce-op reduce-op :combine-op combine-op))

(deftest evaluate-expression-test
  (testing "finds the male patient"
    (with-system-data [system config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code "male"}]
        [:put {:fhir/type :fhir/Patient :id "2" :gender #fhir/code "female"}]]]

      (let [context (context system library-gender)]
        (testing "returning handles"
          (let [context (with-ops context conj-reduce-op into)]
            (given @(cql/evaluate-expression context "InInitialPopulation" "Patient")
              count := 1
              [0 :population-handle :fhir/type] := :fhir/Patient
              [0 :population-handle :id] := "1"
              [0 :subject-handle :fhir/type] := :fhir/Patient
              [0 :subject-handle :id] := "1")))

        (testing "not returning handles"
          (let [context (with-ops context count-reduce-op +)]
            (is (= 1 @(cql/evaluate-expression context "InInitialPopulation" "Patient"))))))))

  (testing "returns all encounters"
    (with-system-data [system config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0-0" :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Encounter :id "1-0" :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]
        [:put {:fhir/type :fhir/Encounter :id "1-1" :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}]
        [:put {:fhir/type :fhir/Patient :id "2"}]]]

      (let [context (context system library-encounter)]
        (testing "returning handles"
          (let [context (-> (with-ops context conj-reduce-op into)
                            (assoc :population-basis "Encounter"))]
            (given @(cql/evaluate-expression context "InInitialPopulation" "Patient")
              count := 3
              [0 :population-handle :fhir/type] := :fhir/Encounter
              [0 :population-handle :id] := "0-0"
              [0 :subject-handle :fhir/type] := :fhir/Patient
              [0 :subject-handle :id] := "0"
              [1 :population-handle :fhir/type] := :fhir/Encounter
              [1 :population-handle :id] := "1-0"
              [1 :subject-handle :fhir/type] := :fhir/Patient
              [1 :subject-handle :id] := "1"
              [2 :population-handle :fhir/type] := :fhir/Encounter
              [2 :population-handle :id] := "1-1"
              [2 :subject-handle :fhir/type] := :fhir/Patient
              [2 :subject-handle :id] := "1")))

        (testing "not returning handles"
          (let [context (-> (with-ops context count-reduce-op +)
                            (assoc :population-basis "Encounter"))]
            (is (= 3 @(cql/evaluate-expression context "InInitialPopulation" "Patient"))))))))

  (testing "missing expression"
    (with-system [system config]
      (let [context (with-ops (context system library-empty) conj-reduce-op into)]
        (given-failed-future (cql/evaluate-expression context "InInitialPopulation" "Patient")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing expression with name `InInitialPopulation`."
          :expression-name := "InInitialPopulation"))))

  (testing "expression context doesn't match the subject type"
    (with-system [system config]
      (let [context (with-ops (context system library-gender) conj-reduce-op into)]
        (given-failed-future (cql/evaluate-expression context "InInitialPopulation" "Encounter")
          ::anom/category := ::anom/incorrect
          ::anom/message := "The context `Patient` of the expression `InInitialPopulation` differs from the subject type `Encounter`."
          :expression-name := "InInitialPopulation"
          :subject-type := "Encounter"
          :expression-context := "Patient"))))

  (testing "population basis doesn't match the expression return type"
    (testing "Boolean"
      (with-system [system config]
        (let [context (with-ops (context system library-encounter) conj-reduce-op into)]
          (given-failed-future (cql/evaluate-expression context "InInitialPopulation" "Patient")
            ::anom/category := ::anom/incorrect
            ::anom/message := "The result type `List<Encounter>` of the expression `InInitialPopulation` differs from the population basis :boolean."
            :expression-name := "InInitialPopulation"
            :population-basis := :boolean
            :expression-result-type := ["Encounter"]))))

    (testing "Encounter"
      (with-system [system config]
        (let [context (-> (context system library-gender)
                          (with-ops conj-reduce-op into)
                          (assoc :population-basis "Encounter"))]
          (given-failed-future (cql/evaluate-expression context "InInitialPopulation" "Patient")
            ::anom/category := ::anom/incorrect
            ::anom/message := "The result type `Boolean` of the expression `InInitialPopulation` differs from the population basis `Encounter`."
            :expression-name := "InInitialPopulation"
            :population-basis := "Encounter"
            :expression-result-type := "Boolean")))))

  (testing "finds the specimen"
    (with-system-data [system config]
      [[[:put {:fhir/type :fhir/Specimen :id "0"}]]]

      (let [context (context system library-specimen)]
        (testing "returning handles"
          (let [context (with-ops context conj-reduce-op into)]
            (given @(cql/evaluate-expression context "InInitialPopulation" "Specimen")
              count := 1
              [0 :population-handle :fhir/type] := :fhir/Specimen
              [0 :population-handle :id] := "0")))

        (testing "not returning handles"
          (let [context (with-ops context count-reduce-op +)]
            (is (= 1 @(cql/evaluate-expression context "InInitialPopulation" "Specimen"))))))))

  (testing "failing eval"
    (with-system-data [system config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (testing "subject-based"
        (let [context (with-ops (context system library-gender) conj-reduce-op into)]
          (with-redefs [expr/eval (failing-eval "msg-222453")]
            (given-failed-future (cql/evaluate-expression context "InInitialPopulation" "Patient")
              ::anom/category := ::anom/fault
              ::anom/message := "Error while evaluating the expression `InInitialPopulation`: msg-222453"))))

      (testing "population-based"
        (let [context (-> (context system library-encounter)
                          (with-ops conj-reduce-op into)
                          (assoc :population-basis "Encounter"))]
          (with-redefs [expr/eval (failing-eval "msg-222453")]
            (given-failed-future (cql/evaluate-expression context "InInitialPopulation" "Patient")
              ::anom/category := ::anom/fault
              ::anom/message := "Error while evaluating the expression `InInitialPopulation`: msg-222453"))))))

  (testing "interrupted"
    (with-system-data [system config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [context (-> (context system library-gender)
                        (with-ops conj-reduce-op into)
                        (assoc :interrupted? (constantly (ba/interrupted "msg-083943"))))]

        (given-failed-future (cql/evaluate-expression context "InInitialPopulation" "Patient")
          ::anom/category := ::anom/interrupted
          ::anom/message := "msg-083943")))))

(deftest evaluate-individual-expression-test
  (testing "counting"
    (testing "match"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]]
        (let [{:keys [db] :as context} (-> (context system library-gender)
                                           (with-ops count-reduce-op +))
              patient (em-tu/resource db "Patient" "0")]
          (is (= 1 @(cql/evaluate-individual-expression context patient "InInitialPopulation"))))))

    (testing "no match"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
        (let [{:keys [db] :as context} (-> (context system library-gender)
                                           (with-ops count-reduce-op +))
              patient (em-tu/resource db "Patient" "0")]
          (is (zero? @(cql/evaluate-individual-expression context patient "InInitialPopulation")))))))

  (testing "returning handles"
    (testing "match"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]]
        (let [{:keys [db] :as context} (-> (context system library-gender)
                                           (with-ops conj-reduce-op into))
              patient (em-tu/resource db "Patient" "0")]

          (given @(cql/evaluate-individual-expression context patient "InInitialPopulation")
            count := 1
            [0 :population-handle :fhir/type] := :fhir/Patient
            [0 :population-handle :id] := "0"
            [0 :subject-handle :fhir/type] := :fhir/Patient
            [0 :subject-handle :id] := "0"))))

    (testing "no match"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
        (let [{:keys [db] :as context} (-> (context system library-gender)
                                           (with-ops conj-reduce-op into))
              patient (em-tu/resource db "Patient" "0")]
          (is (empty? @(cql/evaluate-individual-expression context patient "InInitialPopulation")))))))

  (testing "missing expression"
    (with-system-data [system config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [{:keys [db] :as context} (-> (context system library-empty)
                                         (with-ops conj-reduce-op into))
            patient (em-tu/resource db "Patient" "0")]

        (given-failed-future (cql/evaluate-individual-expression context patient "InInitialPopulation")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing expression with name `InInitialPopulation`."
          :expression-name := "InInitialPopulation"))))

  (testing "error"
    (with-system-data [system config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [{:keys [db] :as context} (-> (context system library-error)
                                         (with-ops conj-reduce-op into)
                                         (assoc :parameters {"Numbers" [1 2]}))
            patient (em-tu/resource db "Patient" "0")]

        (given-failed-future (cql/evaluate-individual-expression context patient "InInitialPopulation")
          ::anom/category := ::anom/conflict
          ::anom/message := "More than one element in `SingletonFrom` expression."
          :fhir/issue := "exception"
          :expression-name := "InInitialPopulation"
          :list := [1 2])))))

(deftest stratum-evaluator-test
  (testing "missing expression"
    (given (cql/stratum-expression-evaluator {} "foo")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing expression with name `foo`."
      :expression-name := "foo"))

  (testing "missing function"
    (given (cql/stratum-expression-evaluator {:population-basis "Encounter"} "foo")
      ::anom/category := ::anom/incorrect
      ::anom/message := "Function definition with name `foo` and arity 1 not found.")))

(deftest stratum-evaluators-test
  (testing "missing expression"
    (given (cql/stratum-expression-evaluators {} ["foo"])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing expression with name `foo`."
      :expression-name := "foo"))

  (testing "missing function"
    (given (cql/stratum-expression-evaluators {:population-basis "Encounter"} ["foo"])
      ::anom/category := ::anom/incorrect
      ::anom/message := "Function definition with name `foo` and arity 1 not found.")))

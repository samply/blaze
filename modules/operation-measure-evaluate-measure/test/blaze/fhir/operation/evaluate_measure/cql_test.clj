(ns blaze.fhir.operation.evaluate-measure.cql-test
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.cql-translator :as cql-translator]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.elm.compiler.library :as library]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.expression :as expr]
    [blaze.fhir.operation.evaluate-measure.cql :as cql]
    [blaze.fhir.operation.evaluate-measure.cql-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type]
    [blaze.fhir.spec.type.system :as system]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock OffsetDateTime]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- now [clock]
  (OffsetDateTime/now ^Clock clock))


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

  parameter Year2022 Date

  context Patient

  define InInitialPopulation:
    Year2022 - 2022 'years'")


(def library-encounter
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define InInitialPopulation:
    [Encounter]")


(def library-encounter-status
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define function Status(encounter Encounter):
    encounter.status")


(defn- compile-library [node cql]
  (when-ok [library (cql-translator/translate cql)]
    (library/compile-library node library {})))


(defn- failing-eval [msg]
  (fn [_ _ _] (throw (Exception. ^String msg))))


(defn- context [{:blaze.db/keys [node] :blaze.test/keys [clock]} library]
  (let [{:keys [expression-defs function-defs]} (compile-library node library)]
    {:db (d/db node)
     :now (now clock)
     :expression-defs expression-defs
     :function-defs function-defs}))


(deftest evaluate-expression-test
  (testing "finds the male patient"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code"male"}]
        [:put {:fhir/type :fhir/Patient :id "2" :gender #fhir/code"female"}]]]

      (let [context (context system library-gender)]
        (given (cql/evaluate-expression context "InInitialPopulation" "Patient" :boolean)
          [0 :population-handle fhir-spec/fhir-type] := :fhir/Patient
          [0 :population-handle :id] := "1"
          [0 :subject-handle fhir-spec/fhir-type] := :fhir/Patient
          [0 :subject-handle :id] := "1"
          count := 1))))

  (testing "returns all encounters"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0-0" :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Encounter :id "1-0" :subject #fhir/Reference{:reference "Patient/1"}}]
        [:put {:fhir/type :fhir/Encounter :id "1-1" :subject #fhir/Reference{:reference "Patient/1"}}]
        [:put {:fhir/type :fhir/Patient :id "2"}]]]

      (let [context (context system library-encounter)]
        (given (cql/evaluate-expression context "InInitialPopulation" "Patient" "Encounter")
          [0 :population-handle fhir-spec/fhir-type] := :fhir/Encounter
          [0 :population-handle :id] := "0-0"
          [0 :subject-handle fhir-spec/fhir-type] := :fhir/Patient
          [0 :subject-handle :id] := "0"
          [1 :population-handle fhir-spec/fhir-type] := :fhir/Encounter
          [1 :population-handle :id] := "1-0"
          [1 :subject-handle fhir-spec/fhir-type] := :fhir/Patient
          [1 :subject-handle :id] := "1"
          [2 :population-handle fhir-spec/fhir-type] := :fhir/Encounter
          [2 :population-handle :id] := "1-1"
          [2 :subject-handle fhir-spec/fhir-type] := :fhir/Patient
          [2 :subject-handle :id] := "1"
          count := 3))))

  (testing "missing expression"
    (with-system [system mem-node-system]
      (let [context (context system library-empty)]
        (given (cql/evaluate-expression context "InInitialPopulation" "Patient" :boolean)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing expression with name `InInitialPopulation`."
          :expression-name := "InInitialPopulation"))))

  (testing "expression context doesn't match the subject type"
    (with-system [system mem-node-system]
      (let [context (context system library-gender)]
        (given (cql/evaluate-expression context "InInitialPopulation" "Encounter" :boolean)
          ::anom/category := ::anom/incorrect
          ::anom/message := "The context `Patient` of the expression `InInitialPopulation` differs from the subject type `Encounter`."
          :expression-name := "InInitialPopulation"
          :subject-type := "Encounter"
          :expression-context := "Patient"))))

  (testing "population basis doesn't match the expression return type"
    (testing "boolean"
      (with-system [system mem-node-system]
        (let [context (context system library-encounter)]
          (given (cql/evaluate-expression context "InInitialPopulation" "Patient" :boolean)
            ::anom/category := ::anom/incorrect
            ::anom/message := "The result type `List<Encounter>` of the expression `InInitialPopulation` differs from the population basis :boolean."
            :expression-name := "InInitialPopulation"
            :population-basis := :boolean
            :expression-result-type := "List<Encounter>"))))

    (testing "Encounter"
      (with-system [system mem-node-system]
        (let [context (context system library-gender)]
          (given (cql/evaluate-expression context "InInitialPopulation" "Patient" "Encounter")
            ::anom/category := ::anom/incorrect
            ::anom/message := "The result type `Boolean` of the expression `InInitialPopulation` differs from the population basis `Encounter`."
            :expression-name := "InInitialPopulation"
            :population-basis := "Encounter"
            :expression-result-type := "Boolean")))))

  (testing "failing eval"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [context (context system library-gender)]
        (with-redefs [expr/eval (failing-eval "msg-222453")]
          (given (cql/evaluate-expression context "InInitialPopulation" "Patient" :boolean)
            ::anom/category := ::anom/fault
            ::anom/message := "Error while evaluating the expression `InInitialPopulation`: msg-222453"))))))


(deftest evaluate-individual-expression-test
  (testing "match"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code"male"}]]]
      (let [{:keys [db] :as context} (context system library-gender)
            patient (d/resource-handle db "Patient" "0")]
        (is (true? (cql/evaluate-individual-expression context patient "InInitialPopulation"))))))

  (testing "no match"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [{:keys [db] :as context} (context system library-gender)
            patient (d/resource-handle db "Patient" "0")]
        (is (false? (cql/evaluate-individual-expression context patient "InInitialPopulation"))))))

  (testing "missing expression"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [{:keys [db] :as context} (context system library-empty)
            patient (d/resource-handle db "Patient" "0")]
        (given (cql/evaluate-individual-expression context patient "InInitialPopulation")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing expression with name `InInitialPopulation`."
          :expression-name := "InInitialPopulation"))))

  (testing "error"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [{:keys [db] :as context} (assoc (context system library-error)
                                       :parameters {"Year2022" (system/date 2022)})
            patient (d/resource-handle db "Patient" "0")]
        (given (cql/evaluate-individual-expression context patient "InInitialPopulation")
          ::anom/category := ::anom/fault
          ::anom/message := "Error while evaluating the expression `InInitialPopulation`: Year 0 out of range while subtracting the period Period[month = 24264, millis = 0] from the year 2022."
          :fhir/issue := "exception"
          :expression-name := "InInitialPopulation"
          :op := :subtract
          :year := (system/date 2022)
          :period := (date-time/period 2022 0 0))))))


(def two-value-eval
  (fn [_ _ _] ["1" "2"]))


(defn- handle [subject-handle]
  {:population-handle subject-handle :subject-handle subject-handle})


(deftest calc-strata-test
  (testing "missing expression"
    (with-system [system mem-node-system]
      (let [context (context system library-empty)]
        (given (cql/calc-strata context "Gender" [])
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing expression with name `Gender`."
          :expression-name := "Gender"))))

  (testing "failing eval"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [db] :as context} (context system library-gender)]
        (with-redefs [expr/eval (failing-eval "msg-221825")]
          (given (cql/calc-strata context "Gender" (mapv handle (d/type-list db "Patient")))
            ::anom/category := ::anom/fault
            ::anom/message := "Error while evaluating the expression `Gender`: msg-221825")))))

  (testing "multiple values"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [db] :as context} (context system library-gender)]
        (with-redefs [expr/eval two-value-eval]
          (given (cql/calc-strata context "Gender" (mapv handle (d/type-list db "Patient")))
            ::anom/category := ::anom/incorrect
            ::anom/message := "CQL expression `Gender` returned more than one value for resource `Patient/0`.")))))

  (testing "gender"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code"male"}]
        [:put {:fhir/type :fhir/Patient :id "2" :gender #fhir/code"female"}]
        [:put {:fhir/type :fhir/Patient :id "3" :gender #fhir/code"male"}]]]

      (let [{:keys [db] :as context} (context system library-gender)
            result (cql/calc-strata context "Gender" (mapv handle (d/type-list db "Patient")))]

        (testing "contains a nil entry for the patient with id 0"
          (given (result nil)
            count := 1
            [0 :subject-handle :id] := "0"
            [0 :population-handle :id] := "0"))

        (testing "contains a male entry for the patients with id 1 and 3"
          (given (result #fhir/code"male")
            count := 2
            [0 :subject-handle :id] := "1"
            [0 :population-handle :id] := "1"
            [1 :subject-handle :id] := "3"
            [1 :population-handle :id] := "3"))

        (testing "contains a female entry for the patient with id 2"
          (given (result #fhir/code"female")
            count := 1
            [0 :subject-handle :id] := "2"
            [0 :population-handle :id] := "2"))))))


(deftest calc-function-strata-test
  (testing "Encounter status"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "1"}]
        [:put {:fhir/type :fhir/Patient :id "2"}]
        [:put {:fhir/type :fhir/Encounter :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Encounter :id "1"
               :status #fhir/code"finished"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Encounter :id "2"
               :status #fhir/code"planned"
               :subject #fhir/Reference{:reference "Patient/1"}}]
        [:put {:fhir/type :fhir/Encounter :id "3"
               :status #fhir/code"finished"
               :subject #fhir/Reference{:reference "Patient/2"}}]]]

      (let [{:keys [db] :as context} (context system library-encounter-status)
            handles
            [{:population-handle (d/resource-handle db "Encounter" "0")
              :subject-handle (d/resource-handle db "Patient" "0")}
             {:population-handle (d/resource-handle db "Encounter" "1")
              :subject-handle (d/resource-handle db "Patient" "0")}
             {:population-handle (d/resource-handle db "Encounter" "2")
              :subject-handle (d/resource-handle db "Patient" "1")}
             {:population-handle (d/resource-handle db "Encounter" "3")
              :subject-handle (d/resource-handle db "Patient" "2")}]
            result (cql/calc-function-strata context "Status" handles)]

        (testing "contains a nil entry for the encounter with id 0"
          (given (result nil)
            count := 1
            [0 :population-handle :id] := "0"
            [0 :subject-handle :id] := "0"))

        (testing "contains a finished entry for the encounters with id 1 and 3"
          (given (result #fhir/code"finished")
            count := 2
            [0 :population-handle :id] := "1"
            [0 :subject-handle :id] := "0"
            [1 :population-handle :id] := "3"
            [1 :subject-handle :id] := "2"))

        (testing "contains a planned entry for the encounter with id 2"
          (given (result #fhir/code"planned")
            count := 1
            [0 :population-handle :id] := "2"
            [0 :subject-handle :id] := "1")))))

  (testing "missing function"
    (with-system [system mem-node-system]
      (let [context (context system library-empty)]
        (given (cql/calc-function-strata context "Gender" [])
          ::anom/category := ::anom/incorrect
          ::anom/message := "Missing function with name `Gender`."
          :function-name := "Gender"))))

  (testing "failing eval"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [{:keys [db] :as context} (context system library-encounter-status)]
        (with-redefs [expr/eval (failing-eval "msg-111807")]
          (given (cql/calc-function-strata context "Status" (mapv handle (d/type-list db "Patient")))
            ::anom/category := ::anom/fault
            ::anom/message := "Error while evaluating the expression `Status`: msg-111807"))))))


(deftest calc-multi-component-strata-test
  (testing "failing eval"
    (with-system-data [system mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
      (let [{:keys [db] :as context} (context system library-gender)]
        (with-redefs [expr/eval (failing-eval "msg-111557")]
          (given (cql/calc-multi-component-strata context ["Gender"] (mapv handle (d/type-list db "Patient")))
            ::anom/category := ::anom/fault
            ::anom/message := "Error while evaluating the expression `Gender`: msg-111557"))))))

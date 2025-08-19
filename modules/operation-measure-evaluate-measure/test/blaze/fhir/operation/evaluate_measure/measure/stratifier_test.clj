(ns blaze.fhir.operation.evaluate-measure.measure.stratifier-test
  (:require
   [blaze.anomaly :refer [when-ok]]
   [blaze.anomaly-spec]
   [blaze.cql.translator :as cql-translator]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler.library :as library]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier :as strat]
   [blaze.fhir.operation.evaluate-measure.measure.stratifier-spec]
   [blaze.fhir.operation.evaluate-measure.test-util :as em-tu]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [java-time.api :as time]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- compile-library [node cql]
  (when-ok [library (cql-translator/translate cql)]
    (library/compile-library node library {})))

(def empty-library
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient")

(def library-age-gender
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define Gender:
    Patient.gender

  define Age:
    AgeInYears()")

(def library-observation-code
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define function Code(observation Observation):
    observation.code")

(def library-observation-value-age
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define function QuantityValue(observation Observation):
    observation.value as Quantity

  define function Age(observation Observation):
    AgeInYearsAt(observation.effective)")

(def library-encounter-status-age
  "library Retrieve
  using FHIR version '4.0.0'
  include FHIRHelpers version '4.0.0'

  context Patient

  define function Status(encounter Encounter):
    encounter.status

  define function Age(encounter Encounter):
    AgeInYearsAt(encounter.period.start)")

(defn- cql-expression [expr]
  {:fhir/type :fhir/Expression
   :language #fhir/code"text/cql-identifier"
   :expression expr})

(def stratifier-with-missing-expression
  {:fhir/type :fhir.Measure.group/stratifier
   :code #fhir/CodeableConcept{:text #fhir/string"gender"}
   :criteria
   {:fhir/type :fhir/Expression
    :language #fhir/code"text/cql-identifier"}})

(def gender-stratifier
  {:fhir/type :fhir.Measure.group/stratifier
   :code #fhir/CodeableConcept{:text #fhir/string"gender"}
   :criteria (cql-expression "Gender")})

(def observation-code-stratifier
  {:fhir/type :fhir.Measure.group/stratifier
   :code #fhir/CodeableConcept{:text #fhir/string"code"}
   :criteria (cql-expression "Code")})

(def observation-value-stratifier
  {:fhir/type :fhir.Measure.group/stratifier
   :code #fhir/CodeableConcept{:text #fhir/string"value"}
   :criteria (cql-expression "QuantityValue")})

(def multi-component-stratifier-with-missing-expression
  {:fhir/type :fhir.Measure.group/stratifier
   :component
   [{:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"age"}
     :criteria
     {:fhir/type :fhir/Expression
      :language #fhir/code"text/cql-identifier"}}
    {:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"gender"}
     :criteria (cql-expression "Gender")}]})

(def age-gender-stratifier
  {:fhir/type :fhir.Measure.group/stratifier
   :component
   [{:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"age"}
     :criteria (cql-expression "Age")}
    {:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"gender"}
     :criteria (cql-expression "Gender")}]})

(def status-age-stratifier
  {:fhir/type :fhir.Measure.group/stratifier
   :component
   [{:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"status"}
     :criteria (cql-expression "Status")}
    {:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"age"}
     :criteria (cql-expression "Age")}]})

(def observation-value-age-stratifier
  {:fhir/type :fhir.Measure.group/stratifier
   :component
   [{:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"value"}
     :criteria (cql-expression "QuantityValue")}
    {:fhir/type :fhir.Measure.group.stratifier/component
     :code #fhir/CodeableConcept{:text #fhir/string"age"}
     :criteria (cql-expression "Age")}]})

(defn- context
  [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock executor]} library]
  (let [{:keys [expression-defs function-defs]} (compile-library node library)]
    (cond->
     {:db (d/db node)
      :now (time/offset-date-time fixed-clock)
      :interrupted? (constantly nil)
      :expression-defs expression-defs
      :executor executor}
      function-defs
      (assoc :function-defs function-defs))))

(def ^:private config
  (assoc mem-node-config
         :blaze.test/executor {}))

(deftest reduce-op-test
  (testing "one component"
    (testing "gender"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Patient :id "2" :gender #fhir/code"female"}]
          [:put {:fhir/type :fhir/Patient :id "3" :gender #fhir/code"male"}]]]

        (let [{:keys [db] :as context} (context system library-age-gender)
              handles (into [] (em-tu/handle-mapper db) (d/type-list db "Patient"))]

          (testing "report-type population"
            (let [op ((strat/reduce-op
                       (assoc context :report-type "population")
                       gender-stratifier) db)]
              (is (= (reduce op (op) handles)
                     {nil 1
                      #fhir/code"male" 2
                      #fhir/code"female" 1}))))

          (testing "report-type subject-list"
            (let [op ((strat/reduce-op
                       (assoc context :report-type "subject-list")
                       gender-stratifier) db)]
              (is (= (reduce op (op) handles)
                     {nil [(nth handles 0)]
                      #fhir/code"male" [(nth handles 1) (nth handles 3)]
                      #fhir/code"female" [(nth handles 2)]})))))))

    (testing "CodeableConcept"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :code #fhir/CodeableConcept
                        {:coding
                         [#fhir/Coding{:system #fhir/uri"http://loinc.org"
                                       :code #fhir/code"17861-6"}]}
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

        (let [{:keys [db] :as context} (context system library-observation-code)
              handles [{:population-handle (em-tu/resource db "Observation" "0")
                        :subject-handle (em-tu/resource db "Patient" "0")}]]

          (testing "report-type population"
            (let [op ((strat/reduce-op
                       (assoc context
                              :report-type "population"
                              :population-basis "Observation")
                       observation-code-stratifier) db)]
              (is (= (reduce op (op) handles)
                     {#fhir/CodeableConcept
                       {:coding
                        [#fhir/Coding{:system #fhir/uri"http://loinc.org"
                                      :code #fhir/code"17861-6"}]}
                      1})))))))

    (testing "Quantity"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :value #fhir/Quantity
                         {:value #fhir/decimal 1M
                          :code #fhir/code"kg"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :value #fhir/Quantity
                         {:value #fhir/decimal 2M}}]
          [:put {:fhir/type :fhir/Observation :id "2"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :value #fhir/Quantity
                         {:value #fhir/decimal 2M}}]]]

        (let [{:keys [db] :as context} (context system library-observation-value-age)
              handles [{:population-handle (em-tu/resource db "Observation" "0")
                        :subject-handle (em-tu/resource db "Patient" "0")}
                       {:population-handle (em-tu/resource db "Observation" "1")
                        :subject-handle (em-tu/resource db "Patient" "0")}
                       {:population-handle (em-tu/resource db "Observation" "2")
                        :subject-handle (em-tu/resource db "Patient" "0")}]]

          (testing "report-type population"
            (let [op ((strat/reduce-op
                       (assoc context
                              :report-type "population"
                              :population-basis "Observation")
                       observation-value-stratifier) db)]
              (is (= (reduce op (op) handles)
                     {#fhir/Quantity{:value #fhir/decimal 1M :code #fhir/code"kg"} 1
                      #fhir/Quantity{:value #fhir/decimal 2M} 2})))))))

    (testing "errors"
      (testing "with expression"
        (with-system [system config]
          (let [context (context system empty-library)]
            (given (strat/reduce-op
                    (assoc context
                           :report-type "population"
                           :group-idx 1
                           :stratifier-idx 2)
                    stratifier-with-missing-expression)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression."
              :fhir/issue := "required"
              :fhir.issue/expression := "Measure.group[1].stratifier[2].criteria"))))

      (testing "with unknown expression"
        (with-system [system config]
          (let [context (context system empty-library)]
            (given (strat/reduce-op (assoc context :report-type "population")
                                    gender-stratifier)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression with name `Gender`."
              :expression-name := "Gender"))))))

  (testing "two components"
    (testing "subject-based measure"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :gender #fhir/code"male"
                 :birthDate #fhir/date"1960"}]
          [:put {:fhir/type :fhir/Patient :id "2"
                 :gender #fhir/code"female"
                 :birthDate #fhir/date"1960"}]
          [:put {:fhir/type :fhir/Patient :id "3"
                 :gender #fhir/code"male"
                 :birthDate #fhir/date"1950"}]]]

        (let [{:keys [db] :as context} (context system library-age-gender)
              handles (into [] (em-tu/handle-mapper db) (d/type-list db "Patient"))]

          (testing "report-type population"
            (let [op ((strat/reduce-op (assoc context :report-type "population")
                                       age-gender-stratifier) db)]
              (is (= (reduce op (op) handles)
                     {[nil nil] 1
                      [10 #fhir/code"female"] 1
                      [10 #fhir/code"male"] 1
                      [20 #fhir/code"male"] 1})))))))

    (testing "Encounter measure"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :birthDate #fhir/date"2000"}]
          [:put {:fhir/type :fhir/Patient :id "1" :birthDate #fhir/date"2001"}]
          [:put {:fhir/type :fhir/Patient :id "2" :birthDate #fhir/date"2003"}]
          [:put {:fhir/type :fhir/Encounter :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Encounter :id "1"
                 :status #fhir/code"finished"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :actualPeriod #fhir/Period{:start #fhir/dateTime"2020"}}]
          [:put {:fhir/type :fhir/Encounter :id "2"
                 :status #fhir/code"planned"
                 :subject #fhir/Reference{:reference "Patient/1"}
                 :actualPeriod #fhir/Period{:start #fhir/dateTime"2021"}}]
          [:put {:fhir/type :fhir/Encounter :id "3"
                 :status #fhir/code"finished"
                 :subject #fhir/Reference{:reference "Patient/2"}
                 :actualPeriod #fhir/Period{:start #fhir/dateTime"2022"}}]
          [:put {:fhir/type :fhir/Encounter :id "4"
                 :status #fhir/code"finished"
                 :subject #fhir/Reference{:reference "Patient/2"}
                 :actualPeriod #fhir/Period{:start #fhir/dateTime"2022"}}]]]

        (let [{:keys [db] :as context} (context system library-encounter-status-age)
              handles [{:population-handle (em-tu/resource db "Encounter" "0")
                        :subject-handle (em-tu/resource db "Patient" "0")}
                       {:population-handle (em-tu/resource db "Encounter" "1")
                        :subject-handle (em-tu/resource db "Patient" "0")}
                       {:population-handle (em-tu/resource db "Encounter" "2")
                        :subject-handle (em-tu/resource db "Patient" "1")}
                       {:population-handle (em-tu/resource db "Encounter" "3")
                        :subject-handle (em-tu/resource db "Patient" "2")}
                       {:population-handle (em-tu/resource db "Encounter" "4")
                        :subject-handle (em-tu/resource db "Patient" "2")}]]

          (testing "report-type population"
            (let [op ((strat/reduce-op
                       (assoc context
                              :report-type "population"
                              :population-basis "Encounter")
                       status-age-stratifier) db)]
              (is (= (reduce op (op) handles)
                     {[nil nil] 1
                      [#fhir/code"finished" 19] 2
                      [#fhir/code"finished" 20] 1
                      [#fhir/code"planned" 20] 1})))))))

    (testing "Quantity"
      (with-system-data [system config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :birthDate #fhir/date"2000"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :effective #fhir/dateTime"2020"
                 :value #fhir/Quantity
                         {:value #fhir/decimal 1M
                          :code #fhir/code"kg"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :effective #fhir/dateTime"2021"
                 :value #fhir/Quantity
                         {:value #fhir/decimal 2M}}]]]

        (let [{:keys [db] :as context} (context system library-observation-value-age)
              handles [{:population-handle (em-tu/resource db "Observation" "0")
                        :subject-handle (em-tu/resource db "Patient" "0")}
                       {:population-handle (em-tu/resource db "Observation" "1")
                        :subject-handle (em-tu/resource db "Patient" "0")}]]

          (testing "report-type population"
            (let [op ((strat/reduce-op
                       (assoc context
                              :report-type "population"
                              :population-basis "Observation")
                       observation-value-age-stratifier) db)]
              (is (= (reduce op (op) handles)
                     {[#fhir/Quantity{:value #fhir/decimal 1M :code #fhir/code"kg"} 20] 1
                      [#fhir/Quantity{:value #fhir/decimal 2M} 21] 1})))))))

    (testing "errors"
      (testing "with expression"
        (with-system [system config]
          (let [context (context system empty-library)]
            (given (strat/reduce-op
                    (assoc context
                           :report-type "population"
                           :group-idx 1
                           :stratifier-idx 2)
                    multi-component-stratifier-with-missing-expression)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression."
              :fhir/issue := "required"
              :fhir.issue/expression := "Measure.group[1].stratifier[2].component[0].criteria"))))

      (testing "with unknown expression"
        (with-system [system config]
          (let [context (context system empty-library)]
            (given (strat/reduce-op (assoc context :report-type "population")
                                    age-gender-stratifier)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression with name `Age`."
              :expression-name := "Age")))))))

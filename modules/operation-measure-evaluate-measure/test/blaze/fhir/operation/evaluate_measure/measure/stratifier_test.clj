(ns blaze.fhir.operation.evaluate-measure.measure.stratifier-test
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.anomaly-spec]
    [blaze.cql-translator :as cql-translator]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.elm.compiler.library :as library]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier :as stratifier]
    [blaze.fhir.operation.evaluate-measure.measure.stratifier-spec]
    [blaze.test-util :as tu :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [java-time.api :as time]
    [juxt.iota :refer [given]])
  (:import
    [java.time Clock OffsetDateTime]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)

(defn- now [clock]
  (OffsetDateTime/now ^Clock clock))


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


(defn- context [{:blaze.db/keys [node] :blaze.test/keys [fixed-clock]} library]
  (let [{:keys [expression-defs function-defs]} (compile-library node library)]
    {:db (d/db node)
     :now (now fixed-clock)
     :timeout-eclipsed? (constantly false)
     :timeout (time/seconds 42)
     :expression-defs expression-defs
     :function-defs function-defs}))


(defn- handle [subject-handle]
  {:population-handle subject-handle :subject-handle subject-handle})


(deftest evaluate
  (testing "one component"
    (testing "gender"
      (with-system-data [system mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Patient :id "1" :gender #fhir/code"male"}]
          [:put {:fhir/type :fhir/Patient :id "2" :gender #fhir/code"female"}]
          [:put {:fhir/type :fhir/Patient :id "3" :gender #fhir/code"male"}]]]

        (let [{:keys [db] :as context} (context system library-age-gender)
              evaluated-populations {:handles [(mapv handle (d/type-list db "Patient"))]}]

          (testing "report-type population"
            (given (stratifier/evaluate (assoc context :report-type "population")
                                        evaluated-populations gender-stratifier)
              [:result :fhir/type] := :fhir.MeasureReport.group/stratifier
              [:result :code 0 :text] := #fhir/string"gender"
              [:result :stratum 0 :value :text] := #fhir/string"female"
              [:result :stratum 0 :population 0 :count] := #fhir/integer 1
              [:result :stratum 1 :value :text] := #fhir/string"male"
              [:result :stratum 1 :population 0 :count] := #fhir/integer 2
              [:result :stratum 2 :value :text] := #fhir/string"null"
              [:result :stratum 2 :population 0 :count] := #fhir/integer 1))

          (testing "report-type subject-list"
            (given (stratifier/evaluate
                     (assoc context
                       :luids ["L0" "L1" "L2"]
                       :report-type "subject-list")
                     evaluated-populations gender-stratifier)
              [:result :fhir/type] := :fhir.MeasureReport.group/stratifier
              [:result :code 0 :text] := #fhir/string"gender"
              [:result :stratum 0 :value :text] := #fhir/string"female"
              [:result :stratum 0 :population 0 :count] := #fhir/integer 1
              [:result :stratum 0 :population 0 :subjectResults :reference] := "List/L2"
              [:result :stratum 1 :value :text] := #fhir/string"male"
              [:result :stratum 1 :population 0 :count] := #fhir/integer 2
              [:result :stratum 1 :population 0 :subjectResults :reference] := "List/L1"
              [:result :stratum 2 :value :text] := #fhir/string"null"
              [:result :stratum 2 :population 0 :count] := #fhir/integer 1
              [:result :stratum 2 :population 0 :subjectResults :reference] := "List/L0"
              [:tx-ops 0 0] := :create
              [:tx-ops 0 1 :fhir/type] := :fhir/List
              [:tx-ops 0 1 :id] := "L0"
              [:tx-ops 0 1 :entry 0 :item :reference] := "Patient/0"
              [:tx-ops 1 1 :id] := "L1"
              [:tx-ops 1 1 :entry 0 :item :reference] := "Patient/1"
              [:tx-ops 1 1 :entry 1 :item :reference] := "Patient/3"
              [:tx-ops 2 1 :id] := "L2"
              [:tx-ops 2 1 :entry 0 :item :reference] := "Patient/2"
              [:tx-ops count] := 3)))))

    (testing "CodeableConcept"
      (with-system-data [system mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :code #fhir/CodeableConcept
                  {:coding
                   [#fhir/Coding{:system #fhir/uri"http://loinc.org"
                                 :code #fhir/code"17861-6"}]}
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]
        (let [{:keys [db] :as context} (context system library-observation-code)
              evaluated-populations
              {:handles
               [[{:population-handle (d/resource-handle db "Observation" "0")
                  :subject-handle (d/resource-handle db "Patient" "0")}]]}]

          (testing "report-type population"
            (given (stratifier/evaluate
                     (assoc context
                       :report-type "population"
                       :population-basis "Observation")
                     evaluated-populations observation-code-stratifier)
              [:result :fhir/type] := :fhir.MeasureReport.group/stratifier
              [:result :code 0 :text] := #fhir/string"code"
              [:result :stratum 0 :value :coding 0 :system] := #fhir/uri"http://loinc.org"
              [:result :stratum 0 :value :coding 0 :code] := #fhir/code"17861-6"
              [:result :stratum 0 :population 0 :count] := #fhir/integer 1)))))

    (testing "Quantity"
      (with-system-data [system mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :value #fhir/Quantity
                  {:value #fhir/decimal 1M
                   :code #fhir/code"kg"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :value #fhir/Quantity
                  {:value #fhir/decimal 2M}}]]]
        (let [{:keys [db] :as context} (context system library-observation-value-age)
              evaluated-populations
              {:handles
               [[{:population-handle (d/resource-handle db "Observation" "0")
                  :subject-handle (d/resource-handle db "Patient" "0")}
                 {:population-handle (d/resource-handle db "Observation" "1")
                  :subject-handle (d/resource-handle db "Patient" "0")}]]}]

          (testing "report-type population"
            (given (stratifier/evaluate
                     (assoc context
                       :report-type "population"
                       :population-basis "Observation")
                     evaluated-populations observation-value-stratifier)
              [:result :fhir/type] := :fhir.MeasureReport.group/stratifier
              [:result :code 0 :text] := #fhir/string"value"
              [:result :stratum 0 :extension 0 :url] := "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.value"
              [:result :stratum 0 :extension 0 :value :value] := #fhir/decimal 1M
              [:result :stratum 0 :extension 0 :value :code] := #fhir/code"kg"
              [:result :stratum 0 :value :text] := "1 kg"
              [:result :stratum 0 :population 0 :count] := #fhir/integer 1
              [:result :stratum 1 :extension 0 :url] := "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.value"
              [:result :stratum 1 :extension 0 :value :value] := #fhir/decimal 2M
              [:result :stratum 1 :value :text] := "2"
              [:result :stratum 1 :population 0 :count] := #fhir/integer 1)))))

    (testing "errors"
      (testing "with expression"
        (with-system [system mem-node-system]
          (let [context (context system empty-library)
                evaluated-populations {:handles [[]]}]
            (given (stratifier/evaluate
                     (assoc context
                       :report-type "population"
                       :group-idx 1
                       :stratifier-idx 2)
                     evaluated-populations
                     stratifier-with-missing-expression)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression."
              :fhir/issue := "required"
              :fhir.issue/expression := "Measure.group[1].stratifier[2].criteria"))))

      (testing "with unknown expression"
        (with-system [system mem-node-system]
          (let [context (context system empty-library)
                evaluated-populations {:handles [[]]}]
            (given (stratifier/evaluate (assoc context :report-type "population")
                                        evaluated-populations gender-stratifier)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression with name `Gender`."
              :expression-name := "Gender"))))

      (testing "gender"
        (with-system-data [system mem-node-system]
          [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

          (let [{:keys [db] :as context} (context system library-age-gender)
                evaluated-populations {:handles [(mapv handle (d/type-list db "Patient"))]}]

            (given (stratifier/evaluate (assoc context
                                          :report-type "population"
                                          :timeout-eclipsed? (constantly true))
                                        evaluated-populations gender-stratifier)
              ::anom/category := ::anom/interrupted
              ::anom/message := "Timeout of 42000 millis eclipsed while evaluating."))))))

  (testing "two components"
    (testing "subject-based measure"
      (with-system-data [system mem-node-system]
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
              evaluated-populations {:handles [(mapv handle (d/type-list db "Patient"))]}]

          (testing "report-type population"
            (given (stratifier/evaluate (assoc context :report-type "population")
                                        evaluated-populations age-gender-stratifier)
              [:result :fhir/type] := :fhir.MeasureReport.group/stratifier
              [:result :code 0 :text] := #fhir/string"age"
              [:result :code 1 :text] := #fhir/string"gender"
              [:result :stratum 0 :component 0 :code :text] := #fhir/string"age"
              [:result :stratum 0 :component 0 :value :text] := #fhir/string"10"
              [:result :stratum 0 :component 1 :code :text] := #fhir/string"gender"
              [:result :stratum 0 :component 1 :value :text] := #fhir/string"female"
              [:result :stratum 0 :population 0 :count] := #fhir/integer 1
              [:result :stratum 1 :component 0 :value :text] := #fhir/string"10"
              [:result :stratum 1 :component 1 :value :text] := #fhir/string"male"
              [:result :stratum 1 :population 0 :count] := #fhir/integer 1
              [:result :stratum 2 :component 0 :value :text] := #fhir/string"20"
              [:result :stratum 2 :component 1 :value :text] := #fhir/string"male"
              [:result :stratum 2 :population 0 :count] := #fhir/integer 1
              [:result :stratum 3 :component 0 :value :text] := #fhir/string"null"
              [:result :stratum 3 :component 1 :value :text] := #fhir/string"null"
              [:result :stratum 3 :population 0 :count] := #fhir/integer 1)))))

    (testing "Encounter measure"
      (with-system-data [system mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0" :birthDate #fhir/date"2000"}]
          [:put {:fhir/type :fhir/Patient :id "1" :birthDate #fhir/date"2001"}]
          [:put {:fhir/type :fhir/Patient :id "2" :birthDate #fhir/date"2003"}]
          [:put {:fhir/type :fhir/Encounter :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]
          [:put {:fhir/type :fhir/Encounter :id "1"
                 :status #fhir/code"finished"
                 :subject #fhir/Reference{:reference "Patient/0"}
                 :period #fhir/Period{:start #fhir/dateTime"2020"}}]
          [:put {:fhir/type :fhir/Encounter :id "2"
                 :status #fhir/code"planned"
                 :subject #fhir/Reference{:reference "Patient/1"}
                 :period #fhir/Period{:start #fhir/dateTime"2021"}}]
          [:put {:fhir/type :fhir/Encounter :id "3"
                 :status #fhir/code"finished"
                 :subject #fhir/Reference{:reference "Patient/2"}
                 :period #fhir/Period{:start #fhir/dateTime"2022"}}]]]
        (let [{:keys [db] :as context} (context system library-encounter-status-age)
              evaluated-populations
              {:handles
               [[{:population-handle (d/resource-handle db "Encounter" "0")
                  :subject-handle (d/resource-handle db "Patient" "0")}
                 {:population-handle (d/resource-handle db "Encounter" "1")
                  :subject-handle (d/resource-handle db "Patient" "0")}
                 {:population-handle (d/resource-handle db "Encounter" "2")
                  :subject-handle (d/resource-handle db "Patient" "1")}
                 {:population-handle (d/resource-handle db "Encounter" "3")
                  :subject-handle (d/resource-handle db "Patient" "2")}]]}]

          (testing "report-type population"
            (given (stratifier/evaluate
                     (assoc context
                       :report-type "population"
                       :population-basis "Encounter")
                     evaluated-populations status-age-stratifier)
              [:result :fhir/type] := :fhir.MeasureReport.group/stratifier
              [:result :code 0 :text] := #fhir/string"status"
              [:result :code 1 :text] := #fhir/string"age"
              [:result :stratum 0 :component 0 :code :text] := #fhir/string"status"
              [:result :stratum 0 :component 0 :value :text] := #fhir/string"finished"
              [:result :stratum 0 :component 1 :code :text] := #fhir/string"age"
              [:result :stratum 0 :component 1 :value :text] := #fhir/string"19"
              [:result :stratum 0 :population 0 :count] := #fhir/integer 1
              [:result :stratum 1 :component 0 :value :text] := #fhir/string"finished"
              [:result :stratum 1 :component 1 :value :text] := #fhir/string"20"
              [:result :stratum 1 :population 0 :count] := #fhir/integer 1
              [:result :stratum 2 :component 0 :value :text] := #fhir/string"null"
              [:result :stratum 2 :component 1 :value :text] := #fhir/string"null"
              [:result :stratum 2 :population 0 :count] := #fhir/integer 1
              [:result :stratum 3 :component 0 :value :text] := #fhir/string"planned"
              [:result :stratum 3 :component 1 :value :text] := #fhir/string"20"
              [:result :stratum 3 :population 0 :count] := #fhir/integer 1)))))

    (testing "Quantity"
      (with-system-data [system mem-node-system]
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
              evaluated-populations
              {:handles
               [[{:population-handle (d/resource-handle db "Observation" "0")
                  :subject-handle (d/resource-handle db "Patient" "0")}
                 {:population-handle (d/resource-handle db "Observation" "1")
                  :subject-handle (d/resource-handle db "Patient" "0")}]]}]

          (testing "report-type population"
            (given (stratifier/evaluate
                     (assoc context
                       :report-type "population"
                       :population-basis "Observation")
                     evaluated-populations observation-value-age-stratifier)
              [:result :fhir/type] := :fhir.MeasureReport.group/stratifier
              [:result :code 0 :text] := #fhir/string"value"
              [:result :code 1 :text] := #fhir/string"age"
              [:result :stratum 0 :component 0 :extension 0 :url] := "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.component.value"
              [:result :stratum 0 :component 0 :extension 0 :value :value] := #fhir/decimal 1M
              [:result :stratum 0 :component 0 :extension 0 :value :code] := #fhir/code"kg"
              [:result :stratum 0 :component 0 :code :text] := #fhir/string"value"
              [:result :stratum 0 :component 0 :value :text] := #fhir/string"1 kg"
              [:result :stratum 0 :component 1 :code :text] := #fhir/string"age"
              [:result :stratum 0 :component 1 :value :text] := #fhir/string"20"
              [:result :stratum 0 :population 0 :count] := #fhir/integer 1
              [:result :stratum 1 :component 0 :extension 0 :url] := "http://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.group.stratifier.stratum.component.value"
              [:result :stratum 1 :component 0 :extension 0 :value :value] := #fhir/decimal 2M
              [:result :stratum 1 :component 0 :value :text] := #fhir/string"2"
              [:result :stratum 1 :component 1 :value :text] := #fhir/string"21"
              [:result :stratum 1 :population 0 :count] := #fhir/integer 1)))))

    (testing "errors"
      (testing "with expression"
        (with-system [system mem-node-system]
          (let [context (context system empty-library)
                evaluated-populations {:handles [[]]}]
            (given (stratifier/evaluate
                     (assoc context
                       :report-type "population"
                       :group-idx 1
                       :stratifier-idx 2)
                     evaluated-populations
                     multi-component-stratifier-with-missing-expression)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression."
              :fhir/issue := "required"
              :fhir.issue/expression := "Measure.group[1].stratifier[2].component[0].criteria"))))

      (testing "with unknown expression"
        (with-system [system mem-node-system]
          (let [context (context system empty-library)
                evaluated-populations {:handles [[]]}]
            (given (stratifier/evaluate (assoc context :report-type "population")
                                        evaluated-populations age-gender-stratifier)
              ::anom/category := ::anom/incorrect
              ::anom/message := "Missing expression with name `Age`."
              :expression-name := "Age")))))))

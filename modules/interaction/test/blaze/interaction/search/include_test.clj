(ns blaze.interaction.search.include-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-system with-system-data]]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.interaction.search.include :as include]
    [blaze.interaction.search.include-spec]
    [blaze.test-util :as tu]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]))


(st/instrument)
(tu/init-fhir-specs)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def non-ref-int-system
  (assoc-in mem-node-system [:blaze.db/node :enforce-referential-integrity] false))


(deftest add-includes-test
  (testing "one direct forward include"
    (testing "enforcing referential integrity"
      (with-system-data [{:blaze.db/keys [node]} mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject
                 #fhir/Reference
                         {:reference "Patient/0"}}]]]

        (let [db (d/db node)
              include-defs {:direct {:forward {"Observation" [{:code "subject"}]}}}
              observations (d/type-list db "Observation")]
          (given (include/add-includes db include-defs observations)
            count := 1
            [0 fhir-spec/fhir-type] := :fhir/Patient))))

    ;; TODO: we have to add to the ResourceSearchParamValue index of the observation in the transaction creating the patient
    ;; TODO: add this test to blaze.db.api-test
    #_(testing "not enforcing referential integrity"
      (with-system-data [{:blaze.db/keys [node]} non-ref-int-system]
        [[[:put {:fhir/type :fhir/Observation :id "0"
                 :subject
                 #fhir/Reference
                         {:reference "Patient/0"}}]]
         [[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [db (d/db node)
              include-defs {:direct {:forward {"Observation" [{:code "subject"}]}}}
              observations (d/type-list db "Observation")]
          (given (include/add-includes db include-defs observations)
            count := 1
            [0 fhir-spec/fhir-type] := :fhir/Patient))))

    (testing "with non-matching target type"
      (with-system-data [{:blaze.db/keys [node]} mem-node-system]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject
                 #fhir/Reference
                         {:reference "Patient/0"}}]]]

        (let [db (d/db node)
              include-defs {:direct
                            {:forward
                             {"Observation"
                              [{:code "subject" :target-type "Group"}]}}}
              observations (d/type-list db "Observation")]
          (is (empty? (include/add-includes db include-defs observations)))))))

  (testing "two direct forward includes with the same type"
    (with-system-data [{:blaze.db/keys [node]} mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "1"
               :subject
               #fhir/Reference
                       {:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :subject
               #fhir/Reference
                       {:reference "Patient/0"}
               :encounter
               #fhir/Reference
                       {:reference "Encounter/1"}}]]]

      (let [db (d/db node)
            include-defs {:direct
                          {:forward
                           {"Observation"
                            [{:code "subject"} {:code "encounter"}]}}}
            observations (d/type-list db "Observation")]
        (given (include/add-includes db include-defs observations)
          count := 2
          [0 fhir-spec/fhir-type] := :fhir/Patient
          [1 fhir-spec/fhir-type] := :fhir/Encounter))))

  (testing "one direct reverse include"
    (with-system-data [{:blaze.db/keys [node]} mem-node-system]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject
               #fhir/Reference
                       {:reference "Patient/0"}}]]]

      (let [db (d/db node)
            include-defs {:direct
                          {:reverse
                           {:any
                            [{:source-type "Observation" :code "subject"}]}}}
            patients (d/type-list db "Patient")]
        (given (include/add-includes db include-defs patients)
          count := 1
          [0 fhir-spec/fhir-type] := :fhir/Observation)))))

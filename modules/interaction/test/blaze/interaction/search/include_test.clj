(ns blaze.interaction.search.include-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.interaction.search.include :as include]
   [blaze.interaction.search.include-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def non-ref-int-config
  (assoc-in mem-node-config [:blaze.db/node :enforce-referential-integrity] false))

(deftest add-includes-test
  (testing "one direct forward include"
    (testing "enforcing referential integrity"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [db (d/db node)
              include-defs {:direct {:forward {"Observation" [{:code "subject"}]}}}
              observations (d/type-list db "Observation")]
          (given (include/add-includes db include-defs observations)
            count := 1
            [0 :fhir/type] := :fhir/Patient))))

    (testing "not enforcing referential integrity"
      (with-system-data [{:blaze.db/keys [node]} non-ref-int-config]
        [[[:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]
         [[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (let [db (d/db node)
              include-defs {:direct {:forward {"Observation" [{:code "subject"}]}}}
              observations (d/type-list db "Observation")]
          (given (include/add-includes db include-defs observations)
            count := 1
            [0 :fhir/type] := :fhir/Patient))))

    (testing "with non-matching target type"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [db (d/db node)
              include-defs {:direct
                            {:forward
                             {"Observation"
                              [{:code "subject" :target-type "Group"}]}}}
              observations (d/type-list db "Observation")]
          (is (empty? (include/add-includes db include-defs observations)))))))

  (testing "two direct forward includes with the same type"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "2"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/1"}}]]]

      (let [db (d/db node)
            include-defs {:direct
                          {:forward
                           {"Observation"
                            [{:code "subject"} {:code "encounter"}]}}}
            observations (d/type-list db "Observation")]
        (given (include/add-includes db include-defs observations)
          count := 2
          [0 :fhir/type] := :fhir/Patient
          [1 :fhir/type] := :fhir/Encounter))))

  (testing "one direct reverse include"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [db (d/db node)
            include-defs {:direct
                          {:reverse
                           {:any
                            [{:source-type "Observation" :code "subject"}]}}}
            patients (d/type-list db "Patient")]
        (given (include/add-includes db include-defs patients)
          count := 1
          [0 :fhir/type] := :fhir/Observation))))

  (testing "direct forward include followed by iterate forward include"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Organization :id "0"}]
        [:put {:fhir/type :fhir/Patient :id "0"
               :managingOrganization #fhir/Reference{:reference #fhir/string "Organization/0"}}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [db (d/db node)
            include-defs {:direct {:forward {"Observation" [{:code "patient"}]}}
                          :iterate {:forward {"Patient" [{:code "organization"}]}}}
            observations (d/type-list db "Observation")]
        (given (include/add-includes db include-defs observations)
          count := 2
          [0 :fhir/type] := :fhir/Organization
          [1 :fhir/type] := :fhir/Patient))))

  (testing "direct forward include followed by iterate reverse include"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [db (d/db node)
            include-defs {:direct {:forward {"Observation" [{:code "subject"}]}}
                          :iterate {:reverse {"Patient" [{:source-type "Condition" :code "patient"}]}}}
            observations (d/type-list db "Observation")]
        (given (include/add-includes db include-defs observations)
          count := 2
          [0 :fhir/type] := :fhir/Condition
          [1 :fhir/type] := :fhir/Patient))))

  (testing "direct reverse include followed by iterate forward include"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/0"}}]
        [:put {:fhir/type :fhir/Encounter :id "0"}]]]

      (let [db (d/db node)
            include-defs {:direct {:reverse {"Patient" [{:source-type "Condition" :code "subject"}]}}
                          :iterate {:forward {"Condition" [{:code "encounter"}]}}}
            patients (d/type-list db "Patient")]
        (given (include/add-includes db include-defs patients)
          count := 2
          [0 :fhir/type] := :fhir/Condition
          [1 :fhir/type] := :fhir/Encounter))))

  (testing "direct reverse include followed by iterate reverse include"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Encounter :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :encounter #fhir/Reference{:reference #fhir/string "Encounter/0"}}]]]

      (let [db (d/db node)
            include-defs {:direct {:reverse {"Patient" [{:source-type "Encounter" :code "subject"}]}}
                          :iterate {:reverse {"Encounter" [{:source-type "Condition" :code "encounter"}]}}}
            patients (d/type-list db "Patient")]
        (given (include/add-includes db include-defs patients)
          count := 2
          [0 :fhir/type] := :fhir/Condition
          [1 :fhir/type] := :fhir/Encounter)))))

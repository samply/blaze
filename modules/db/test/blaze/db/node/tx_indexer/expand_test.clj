(ns blaze.db.node.tx-indexer.expand-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.node]
   [blaze.db.node.tx-indexer.expand :as expand]
   [blaze.db.node.tx-indexer.expand-spec]
   [blaze.db.search-param-registry]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.db.tx-cache]
   [blaze.db.tx-log.local]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.spec]
   [blaze.fhir.spec.type]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(def patient-0 {:fhir/type :fhir/Patient :id "0"})

(defn- expand-tx-cmds [node tx-cmds]
  (with-open [db (d/new-batch-db (d/db node))]
    (expand/expand-tx-cmds db tx-cmds)))

(deftest expand-tx-cmds-conditional-create-test
  (testing "conflict"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "0"
               :birthDate #fhir/date "2020"}]
        [:put {:fhir/type :fhir/Patient :id "1"
               :birthDate #fhir/date "2020"}]]]

      (given (expand-tx-cmds
              node
              [{:op "create" :type "Patient" :id "foo"
                :hash (hash/generate patient-0)
                :if-none-exist [["birthdate" "2020"]]}])
        ::anom/category := ::anom/conflict
        ::anom/message := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
        :http/status := 412)))
  (testing "match"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:put {:fhir/type :fhir/Patient :id "2"
               :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]]]

      (given (expand-tx-cmds
              node
              [{:op "create" :type "Patient" :id "0"
                :hash (hash/generate patient-0)
                :if-none-exist [["identifier" "120426"]]}])
        count := 1
        [0 :op] := "hold"
        [0 :type] := "Patient"
        [0 :id] := "2"
        [0 :if-none-exist] := [["identifier" "120426"]]))))

(deftest expand-tx-cmds-conditional-delete-test
  (testing "success"
    (testing "no match"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]]]

        (is (empty? (expand-tx-cmds
                     node
                     [{:op "conditional-delete" :type "Patient"
                       :clauses [["identifier" "foo"]]}])))))

    (testing "one patient match"
      (with-system-data [{:blaze.db/keys [node]} config]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]]]

        (given (expand-tx-cmds
                node
                [{:op "conditional-delete" :type "Patient"
                  :clauses [["identifier" "120426"]]}])
          count := 1
          [0 :op] := "delete"
          [0 :type] := "Patient"
          [0 :id] := "0")))

    (testing "two patient matches"
      (testing "is forbidden by default"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(vec (for [id ["0" "1"]]
                  [:create {:fhir/type :fhir/Patient :id id
                            :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]))]

          (given (expand-tx-cmds node
                                 [{:op "conditional-delete" :type "Patient"
                                   :clauses [["identifier" "120426"]]}])
            ::anom/category := ::anom/conflict
            ::anom/message := "Conditional delete of one single Patient with query `identifier=120426` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."
            :http/status := 412)))

      (testing "fails on more then 10,000 matches"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(vec (for [id (range 10001)]
                  [:create {:fhir/type :fhir/Patient :id (str id)
                            :identifier [#fhir/Identifier{:value #fhir/string "120426"}]}]))]

          (given (expand-tx-cmds node
                                 [{:op "conditional-delete" :type "Patient"
                                   :clauses [["identifier" "120426"]]
                                   :allow-multiple true}])
            ::anom/category := ::anom/conflict
            ::anom/message := "Conditional delete of Patients with query `identifier=120426` failed because more than 10,000 matches were found."
            :fhir/issue "too-costly")))

      (testing "works if allowed"
        (with-system-data [{:blaze.db/keys [node]} config]
          [(vec (for [id ["0" "1"]]
                  [:create {:fhir/type :fhir/Patient :id id
                            :identifier [#fhir/Identifier{:value #fhir/string  "120426"}]}]))]

          (given (expand-tx-cmds node
                                 [{:op "conditional-delete" :type "Patient"
                                   :clauses [["identifier" "120426"]]
                                   :allow-multiple true}])
            count := 2
            [0 :op] := "delete"
            [0 :type] := "Patient"
            [0 :id] := "0"
            [1 :op] := "delete"
            [1 :type] := "Patient"
            [1 :id] := "1"))))))

(deftest expand-tx-cmds-patient-purge-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} config]
      (is (empty? (expand-tx-cmds node [{:op "patient-purge" :id "0"}])))))

  (testing "patient only"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]]]

      (given (expand-tx-cmds node [{:op "patient-purge" :id "0"}])
        count := 1
        [0 :op] := "purge"
        [0 :type] := "Patient"
        [0 :id] := "0"
        [0 :check-refs] := false)

      (given (expand-tx-cmds node [{:op "patient-purge" :id "0" :check-refs true}])
        count := 1
        [0 :op] := "purge"
        [0 :type] := "Patient"
        [0 :id] := "0"
        [0 :check-refs] := true)))

  (testing "patient with one observation"
    (with-system-data [{:blaze.db/keys [node]} config]
      [[[:create {:fhir/type :fhir/Patient :id "0"}]
        [:create {:fhir/type :fhir/Observation :id "0"
                  :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (given (expand-tx-cmds node [{:op "patient-purge" :id "0"}])
        count := 2
        [0 :op] := "purge"
        [0 :type] := "Patient"
        [0 :id] := "0"
        [1 :op] := "purge"
        [1 :type] := "Observation"
        [1 :id] := "0"))))

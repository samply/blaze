(ns blaze.db.impl.query.system-test
  (:require
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.query.system :as qs]
   [blaze.db.impl.query.system-spec]
   [blaze.db.test-util :refer [config with-system-data]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest system-query-execute-test
  (with-system-data [{:blaze.db/keys [node]} config]
    [[[:put {:fhir/type :fhir/Patient :id "0"
             :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-131647" :code #fhir/code "code-131652"}]}}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :meta #fhir/Meta{:tag [#fhir/Coding{:system #fhir/uri "system-131647" :code #fhir/code "code-131652"}]}}]]]

    ;; the clauses are taken from a query compiled over all types, while the
    ;; queries under test are restricted to a subset of types in order to test
    ;; starting with a type that isn't part of the query
    (let [{:keys [clauses]} @(d/compile-system-query node [["_tag" "system-131647|code-131652"]])]

      (testing "starting with a type coming before the only type of the query
                returns all matches of that type"
        ;; in type hash order, Observation comes before Patient
        (let [query (qs/->SystemQuery [(codec/tid "Patient")] clauses)]
          (with-open [db (d/new-batch-db (d/db node))]
            (given (vec (d/execute-query db query "Observation" "0"))
              count := 1
              [0 :fhir/type] := :fhir/Patient
              [0 :id] := "0"))))

      (testing "starting with a type coming after every type of the query
                returns no matches"
        (let [query (qs/->SystemQuery [(codec/tid "Observation")] clauses)]
          (with-open [db (d/new-batch-db (d/db node))]
            (is (coll/empty? (d/execute-query db query "Patient" "0")))))))))

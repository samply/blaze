(ns blaze.db.node.tx-indexer.verify.impl-test
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.api :as d]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.tx-indexer.verify.impl :as impl]
   [blaze.db.test-util :as dtu :refer [with-db with-system-data]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]])
  (:import
   [com.github.benmanes.caffeine.cache.stats CacheStats]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def patient-tid (codec/tid "Patient"))
(def observation-tid (codec/tid "Observation"))
(def condition-tid (codec/tid "Condition"))

(defn- rev-include-param [source-type code]
  [source-type (codec/tid source-type) (codec/c-hash code)])

(def stats
  "Stats of a database with one Patient and one Observation and a Condition
  that was deleted."
  {:types {patient-tid {:total 1 :num-changes 1}
           observation-tid {:total 1 :num-changes 1}
           condition-tid {:total 0 :num-changes 2}}
   :system {:total 2 :num-changes 4}})

(deftest rev-include-params-test
  (with-system [{search-param-registry ::dtu/search-param-registry}
                dtu/search-param-registry-config]

    (testing "empty database"
      (is (empty? (impl/rev-include-params search-param-registry
                                           node-stats/empty-stats "Patient"))))

    (let [params (impl/rev-include-params search-param-registry stats "Patient")]

      (testing "contains the search params of Observation pointing to Patient"
        (is (some #{(rev-include-param "Observation" "subject")} params))
        (is (some #{(rev-include-param "Observation" "patient")} params)))

      (testing "contains the search params of Patient pointing to Patient"
        (is (some #{(rev-include-param "Patient" "link")} params)))

      (testing "skips source types without resources"
        (is (every? #{"Patient" "Observation"} (map first params)))))))

(deftest rev-include-test
  (with-system-data [{:blaze.db/keys [node]} dtu/config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:put {:fhir/type :fhir/Observation :id "1"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

    (with-db [db node]
      (let [reference (rh/tid-id (d/resource-handle db "Patient" "0"))
            param (rev-include-param "Observation" "subject")]

        (testing "returns all resources pointing to the patient"
          (given (vec (impl/rev-include db reference param #{}))
            count := 2
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "0"
            [1 :fhir/type] := :fhir/Observation
            [1 :id] := "1"))

        (testing "skips resources that are deleted"
          (given (vec (impl/rev-include db reference param
                                        #{["Observation" "0"]}))
            count := 1
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "1"))

        (testing "only skips deleted resources of the source type"
          (is (= 2 (count (vec (impl/rev-include db reference param
                                                 #{["Condition" "0"]}))))))))))

(defn- num-lookups
  "Returns the number of lookups, hits and misses, of `resource-handle-cache`."
  [resource-handle-cache]
  (.requestCount ^CacheStats (ccp/-stats resource-handle-cache)))

(deftest rev-include-lookups-test
  (with-system-data [{:blaze.db/keys [node resource-handle-cache]}
                     dtu/resource-handle-cache-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:put {:fhir/type :fhir/Observation :id "1"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

    (with-db [db node]
      (let [reference (rh/tid-id (d/resource-handle db "Patient" "0"))
            param (rev-include-param "Observation" "subject")
            lookups-before (num-lookups resource-handle-cache)]

        (testing "looks up only the resource handle of the resource that isn't deleted"
          (given (vec (impl/rev-include db reference param #{["Observation" "0"]}))
            count := 1
            [0 :id] := "1")
          (is (= 1 (- (num-lookups resource-handle-cache) lookups-before))))))))

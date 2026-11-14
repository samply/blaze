(ns blaze.db.node.tx-indexer.verify.impl-test
  (:require
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.api :as d]
   [blaze.db.impl.index.index-handle :as ih]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.util-spec]
   [blaze.db.node.tx-indexer.verify.impl :as impl]
   [blaze.db.test-util :as dtu :refer [with-db with-system-data]]
   [blaze.fhir.spec.type :as type]
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

(deftest rev-include-params-test
  (testing "empty database"
    (with-system [{:blaze.db/keys [node]} dtu/config]
      (with-db [db node]
        (is (empty? (impl/rev-include-params db node-stats/empty-stats
                                             "Patient" true))))))

  (with-system-data [{:blaze.db/keys [node]} dtu/config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Practitioner :id "0"}]
      [:put {:fhir/type :fhir/Condition :id "0"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]
     [[:put {:fhir/type :fhir/Patient :id "1"
             :link [{:fhir/type :fhir.Patient/link
                     :other #fhir/Reference{:reference #fhir/string "Patient/0"}
                     :type #fhir/code "seealso"}]}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
             :performer [#fhir/Reference{:reference #fhir/string "Practitioner/0"}]}]
      [:delete "Condition" "0"]]]

    (with-db [db node]
      (testing "with several deletes"
        (let [params (impl/rev-include-params db @(:stats node) "Patient" true)]

          (testing "contains the search params of Observation pointing to Patient"
            (is (some #{(tx-u/rev-include-param "Observation" "subject")} params))
            (is (some #{(tx-u/rev-include-param "Observation" "patient")} params)))

          (testing "contains the search params of Patient pointing to Patient"
            (is (some #{(tx-u/rev-include-param "Patient" "link")} params)))

          (testing "skips source types without resources"
            (is (every? #{"Patient" "Observation"} (map first params))))

          (testing "skips search params without references to resources of the type"
            (testing "because they don't hold any reference"
              (is (not-any? #{(tx-u/rev-include-param "Observation" "focus")} params)))

            (testing "because they hold only references to other types"
              (is (not-any? #{(tx-u/rev-include-param "Observation" "performer")}
                            params))))))

      (testing "with a single delete"
        (let [params (impl/rev-include-params db @(:stats node) "Patient" false)]

          (testing "skips source types without resources"
            (is (every? #{"Patient" "Observation"} (map first params))))

          (testing "keeps search params without references to resources of the
                    type"
            (is (some #{(tx-u/rev-include-param "Observation" "focus")} params))
            (is (some #{(tx-u/rev-include-param "Observation" "performer")}
                      params))))))))

(deftest type-may-match-test
  (with-system [{:blaze.db/keys [node]} dtu/config]
    (dtu/transact-internal
     node
     [[:create dtu/internal-patient-0]
      [:create {:fhir/type :fhir/Observation :id "0"}]
      [:create {:fhir/type :fhir/Condition :id "0"
                :meta (type/meta {:security [dtu/internal-label]})}]])
    (dtu/transact-internal
     node
     [[:put {:fhir/type :fhir/Condition :id "0"}]])

    (with-db [db node]
      (let [matcher (:internal-matcher node)]

        (testing "a type with a resource labelled internal may match"
          (is (true? (impl/type-may-match? db matcher "Patient"))))

        (testing "a type with a resource labelled internal only in a version no
                  longer current may match"
          (is (true? (impl/type-may-match? db matcher "Condition"))))

        (testing "a type without resources labelled internal doesn't match"
          (is (false? (impl/type-may-match? db matcher "Observation"))))

        (testing "a type without resources doesn't match"
          (is (false? (impl/type-may-match? db matcher "Encounter"))))))))

(defn- rev-include-colliding
  "Returns a vector of what `impl/rev-include` returns, as if the hash prefix of
  every resource handle matched every index entry. Real hashes can't produce
  such a collision on purpose."
  [db reference rev-include-param deletes]
  (with-redefs [ih/matches-hash? (constantly true)]
    (vec (impl/rev-include db reference rev-include-param deletes))))

(deftest rev-include-test
  (with-system-data [{:blaze.db/keys [node]} dtu/config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]
      [:put {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
      [:put {:fhir/type :fhir/Observation :id "1"
             :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

    (with-db [db node]
      (let [reference (rh/tid-id (d/resource-handle db "Patient" "0"))
            param (tx-u/rev-include-param "Observation" "subject")]

        (testing "returns all resources pointing to the patient"
          (given (vec (impl/rev-include db reference param {}))
            count := 2
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "0"
            [1 :fhir/type] := :fhir/Observation
            [1 :id] := "1"))

        (testing "skips resources that are deleted"
          (given (vec (impl/rev-include db reference param
                                        {"Observation" (impl/assoc-delete nil "0" true)}))
            count := 1
            [0 :fhir/type] := :fhir/Observation
            [0 :id] := "1"))

        (testing "only skips deleted resources of the source type"
          (is (= 2 (count (vec (impl/rev-include db reference param
                                                 {"Condition" (impl/assoc-delete nil "0" true)})))))))))

  (testing "skips resources deleted before, even if the hash prefix of their
            deleted version matches an index entry of an earlier version"
    (with-system-data [{:blaze.db/keys [node]} dtu/config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]
       [[:delete "Observation" "0"]]]

      (with-db [db node]
        (let [reference (rh/tid-id (d/resource-handle db "Patient" "0"))
              param (tx-u/rev-include-param "Observation" "subject")]
          (is (empty? (rev-include-colliding db reference param {}))))))))

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
            param (tx-u/rev-include-param "Observation" "subject")
            lookups-before (num-lookups resource-handle-cache)]

        (testing "looks up only the resource handle of the resource that isn't deleted"
          (given (vec (impl/rev-include db reference param {"Observation" (impl/assoc-delete nil "0" true)}))
            count := 1
            [0 :id] := "1")
          (is (= 1 (- (num-lookups resource-handle-cache) lookups-before))))))))

(defn- deletes-of
  "Returns the shards of the deletes of a type with `ids` mapped to true."
  [ids]
  (reduce #(impl/assoc-delete %1 %2 true) nil ids))

(deftest assoc-delete-test
  (let [shards (deletes-of (map str (range 100)))]
    (testing "every id added is found"
      (is (every? #(true? (impl/delete-value shards (str %))) (range 100))))

    (testing "an id not added isn't found"
      (is (nil? (impl/delete-value shards "100"))))

    (testing "the ids are spread over several shards"
      (is (< 1 (count (filter seq shards)))))))

(deftest merge-delete-shard-test
  (testing "the shards merged from the ones of several parts hold the ids of
            all parts"
    (let [parts [(deletes-of (map str (range 100)))
                 (deletes-of (map str (range 100 200)))]
          merged (mapv (partial impl/merge-delete-shard parts)
                       (range impl/num-delete-shards))]
      (is (every? #(true? (impl/delete-value merged (str %))) (range 200)))
      (is (nil? (impl/delete-value merged "200"))))))

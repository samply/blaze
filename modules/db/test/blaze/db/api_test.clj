(ns blaze.db.api-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.kv.mem :refer [init-mem-kv-store]]
    [blaze.db.search-param-registry :as sr]
    [blaze.db.indexer.resource :refer [init-resource-indexer]]
    [blaze.db.indexer.tx :refer [init-tx-indexer]]
    [blaze.db.node :as node :refer [init-node]]
    [blaze.db.node-spec]
    [blaze.db.tx-log.local :refer [init-local-tx-log]]
    [blaze.db.tx-log.local-spec]
    [blaze.db.tx-log-spec]
    [blaze.executors :as ex]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [manifold.deferred :as md])
  (:import
    [java.time Clock Instant ZoneId]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-mem-search-param-registry))


(defn new-node []
  (let [kv-store
        (init-mem-kv-store
          {:search-param-value-index nil
           :resource-value-index nil
           :compartment-search-param-value-index nil
           :compartment-resource-value-index nil
           :resource-type-index nil
           :compartment-resource-type-index nil
           :resource-index nil
           :active-search-params nil
           :tx-success-index nil
           :tx-error-index nil
           :t-by-instant-index nil
           :resource-as-of-index nil
           :type-as-of-index nil
           :system-as-of-index nil
           :type-stats-index nil
           :system-stats-index nil})
        r-i (init-resource-indexer
              search-param-registry kv-store
              (ex/cpu-bound-pool "resource-indexer-%d"))
        tx-i (init-tx-indexer kv-store)
        clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC"))
        tx-log (init-local-tx-log r-i 1 tx-i clock)
        resource-cache (node/resource-cache kv-store 0)]
    (init-node tx-log tx-i kv-store resource-cache search-param-registry)))


(deftest submit-tx
  (testing "create"
    (let [node (new-node)]
      @(d/submit-tx node [[:create {:resourceType "Patient" :id "0"}]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "1"
        [meta :blaze.db/op] := :create)))

  (testing "put"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "1"
        [meta :blaze.db/op] := :put)))

  (testing "a transaction with duplicate resources fails"
    (given @(-> (d/submit-tx (new-node) [[:put {:resourceType "Patient" :id "0"}]
                                         [:put {:resourceType "Patient" :id "0"}]])
                (md/catch' identity))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Duplicate resource `Patient/0`.")))


(deftest compartment-query-batch
  (testing "one Observation"
    (let [node (new-node)
          batch-fn (d/compartment-query-batch
                     node "Patient" "Observation" [["code" "code-132308"]])]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put {:resourceType "Observation" :id "0"
                 :code {:coding [{:code "code-132308"}]}
                 :subject {:reference "Patient/0"}}]])
      (given (d/ri-first (batch-fn (d/db node) "0"))
        :resourceType := "Observation"
        :id := "0")))

  (testing "a deleted resource does not show up"
    (let [node (new-node)
          batch-fn (d/compartment-query-batch
                     node "Patient" "Observation" [["code" "code-132308"]])]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0"}]
          [:put {:resourceType "Observation" :id "0"
                 :code {:coding [{:code "code-132308"}]}
                 :subject {:reference "Patient/0"}}]
          [:put {:resourceType "Observation" :id "1"
                 :code {:coding [{:code "code-132308"}]}
                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:delete "Observation" "0"]])
      (given (into [] (batch-fn (d/db node) "0"))
        [0 :resourceType] := "Observation"
        [0 :id] := "1"
        1 := nil)))

  (testing "two Observations"
    (testing "alone"
      (let [node (new-node)
            batch-fn (d/compartment-query-batch
                       node "Patient" "Observation" [["code" "code-132308"]])]
        @(d/submit-tx
           node
           [[:put {:resourceType "Patient" :id "0"}]
            [:put {:resourceType "Observation" :id "0"
                   :code {:coding [{:code "code-132308"}]}
                   :subject {:reference "Patient/0"}}]
            [:put {:resourceType "Observation" :id "1"
                   :code {:coding [{:code "code-132308"}]}
                   :subject {:reference "Patient/0"}}]])
        (given (into [] (batch-fn (d/db node) "0"))
          [0 :resourceType] := "Observation"
          [0 :id] := "0"
          [1 :resourceType] := "Observation"
          [1 :id] := "1")))

    (testing "with one unrelated Observation with other code"
      (let [node (new-node)
            batch-fn (d/compartment-query-batch
                       node "Patient" "Observation" [["code" "code-132308"]])]
        @(d/submit-tx
           node
           [[:put {:resourceType "Patient" :id "0"}]
            [:put {:resourceType "Observation" :id "0"
                   :code {:coding [{:code "code-132308"}]}
                   :subject {:reference "Patient/0"}}]
            [:put {:resourceType "Observation" :id "1"
                   :code {:coding [{:code "code-132308"}]}
                   :subject {:reference "Patient/0"}}]
            [:put {:resourceType "Observation" :id "2"
                   :code {:coding [{:code "code-143015"}]}
                   :subject {:reference "Patient/0"}}]])
        (given (into [] (batch-fn (d/db node) "0"))
          [0 :resourceType] := "Observation"
          [0 :id] := "0"
          [1 :resourceType] := "Observation"
          [1 :id] := "1"
          2 := nil)))

    (testing "with one unrelated Observation of an other patient"
      (let [node (new-node)
            batch-fn (d/compartment-query-batch
                       node "Patient" "Observation" [["code" "code-132308"]])]
        @(d/submit-tx
           node
           [[:put {:resourceType "Patient" :id "0"}]
            [:put {:resourceType "Patient" :id "1"}]
            [:put {:resourceType "Observation" :id "0"
                   :code {:coding [{:code "code-132308"}]}
                   :subject {:reference "Patient/0"}}]
            [:put {:resourceType "Observation" :id "1"
                   :code {:coding [{:code "code-132308"}]}
                   :subject {:reference "Patient/0"}}]
            [:put {:resourceType "Observation" :id "2"
                   :code {:coding [{:code "code-132308"}]}
                   :subject {:reference "Patient/1"}}]])
        (given (into [] (batch-fn (d/db node) "0"))
          [0 :resourceType] := "Observation"
          [0 :id] := "0"
          [1 :resourceType] := "Observation"
          [1 :id] := "1"
          2 := nil)))))


(deftest resource
  (testing "a new node does not contain a resource"
    (is (nil? (d/resource (d/db (new-node)) "Patient" "0"))))

  (testing "a node contains a resource after a put transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "1")))

  (testing "a node contains a resource after a put transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])
      (given (d/resource (d/db node) "Patient" "0")
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "2"
        [meta :blaze.db/op] := :delete))))


(deftest list-resources
  (testing "a new node has a empty list of resources"
    (is (d/ri-empty? (d/list-resources (d/db (new-node)) "Patient"))))

  (testing "a node contains one resource after a put transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (first (into [] (d/list-resources (d/db node) "Patient")))
        :resourceType := "Patient"
        :id := "0"
        [:meta :versionId] := "1")))

  (testing "a node contains two resources after a put transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])
      (given (into [] (d/list-resources (d/db node) "Patient"))
        [0 :resourceType] := "Patient"
        [0 :id] := "0"
        [0 :meta :versionId] := "1"
        [1 :resourceType] := "Patient"
        [1 :id] := "1"
        [1 :meta :versionId] := "2")))

  (testing "a deleted resource does not show up"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])
      (is (d/ri-empty? (d/list-resources (d/db node) "Patient")))))

  (testing "a resource submitted after getting the db does not show up"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (let [db (d/db node)]
        @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])
        (given (into [] (d/list-resources db "Patient"))
          [0 :resourceType] := "Patient"
          [0 :id] := "0"
          [0 :meta :versionId] := "1"
          1 := nil))))

  (testing "it is possible to start at a later id"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "2"}]])
      (given (into [] (d/list-resources (d/db node) "Patient" "1"))
        [0 :resourceType] := "Patient"
        [0 :id] := "1"
        [0 :meta :versionId] := "2"
        [1 :resourceType] := "Patient"
        [1 :id] := "2"
        [1 :meta :versionId] := "3"
        2 := nil)))

  (testing "it is possible to start at a later id"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "1"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "2"}]])
      (given (into [] (d/list-resources (d/db node) "Patient" "1"))
        [0 :resourceType] := "Patient"
        [0 :id] := "1"
        [0 :meta :versionId] := "2"
        [1 :resourceType] := "Patient"
        [1 :id] := "2"
        [1 :meta :versionId] := "3"
        2 := nil)))

  (testing "later types do not show up"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (into [] (d/list-resources (d/db node) "Observation"))
        [0 :resourceType] := "Observation"
        [0 :id] := "0"
        1 := nil))))


(deftest list-compartment-resources
  (testing "a new node has a empty list of resources in the Patient/0 compartment"
    (let [db (d/db (new-node))]
      (is (d/ri-empty? (d/list-compartment-resources db "Patient" "0" "Observation")))))

  (testing "a node contains one Observation in the Patient/0 compartment"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      (given (d/ri-first (d/list-compartment-resources (d/db node) "Patient" "0" "Observation"))
        :resourceType := "Observation"
        :id := "0"
        [:meta :versionId] := "2")))

  (testing "a node contains two resources in the Patient/0 compartment"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "1"
                                 :subject {:reference "Patient/0"}}]])
      (given (into [] (d/list-compartment-resources (d/db node) "Patient" "0" "Observation"))
        [0 :resourceType] := "Observation"
        [0 :id] := "0"
        [0 :meta :versionId] := "2"
        [1 :resourceType] := "Observation"
        [1 :id] := "1"
        [1 :meta :versionId] := "3")))

  (testing "a deleted resource does not show up"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:delete "Observation" "0"]])
      (is (d/ri-empty? (d/list-compartment-resources (d/db node) "Patient" "0" "Observation")))))

  (testing "it is possible to start at a later id"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "1"
                                 :subject {:reference "Patient/0"}}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "2"
                                 :subject {:reference "Patient/0"}}]])
      (given (into [] (d/list-compartment-resources
                        (d/db node) "Patient" "0" "Observation" "1"))
        [0 :resourceType] := "Observation"
        [0 :id] := "1"
        [0 :meta :versionId] := "3"
        [1 :resourceType] := "Observation"
        [1 :id] := "2"
        [1 :meta :versionId] := "4"
        2 := nil))))


(deftest type-query
  (testing "a new node has a empty list of resources"
    (let [db (d/db (new-node))]
      (is (d/ri-empty? (d/type-query db "Patient" [["gender" "male"]])))))

  (testing "finds the Patient"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0" :gender "male"}]])
      (given (d/ri-first (d/type-query (d/db node) "Patient" [["gender" "male"]]))
        :resourceType := "Patient"
        :id := "0")))

  (testing "finds only the male Patient"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0" :gender "male"}]
          [:put {:resourceType "Patient" :id "1" :gender "female"}]])
      (given (into [] (d/type-query (d/db node) "Patient" [["gender" "male"]]))
        [0 :resourceType] := "Patient"
        [0 :id] := "0"
        1 := nil)))

  (testing "does not find the deleted male Patient"
    (let [node (new-node)]
      @(d/submit-tx
         node
         [[:put {:resourceType "Patient" :id "0" :gender "male"}]
          [:put {:resourceType "Patient" :id "1" :gender "male"}]])
      @(d/submit-tx
         node
         [[:delete "Patient" "1"]])
      (given (into [] (d/type-query (d/db node) "Patient" [["gender" "male"]]))
        [0 :resourceType] := "Patient"
        [0 :id] := "0"
        1 := nil))))


(deftest compartment-query
  (testing "a new node has a empty list of resources in the Patient/0 compartment"
    (let [db (d/db (new-node))]
      (is (d/ri-empty? (d/compartment-query db "Patient" "0" "Observation" [["code" "foo"]])))))

  (testing "a node contains one resource in the Patient/0 compartment"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}
                                 :code
                                 {:coding
                                  [{:system "system-191514"
                                    :code "code-191518"}]}}]])
      (given (d/ri-first (d/compartment-query
                           (d/db node) "Patient" "0" "Observation"
                           [["code" "system-191514|code-191518"]]))
        :resourceType := "Observation"
        :id := "0"
        [:meta :versionId] := "2")))

  (testing "only one Observation is found"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:put {:resourceType "Observation" :id "0"
                                 :subject {:reference "Patient/0"}
                                 :code
                                 {:coding
                                  [{:system "system"
                                    :code "code-1"}]}}]
                          [:put {:resourceType "Observation" :id "1"
                                 :subject {:reference "Patient/0"}
                                 :code
                                 {:coding
                                  [{:system "system"
                                    :code "code-2"}]}}]])
      (given (into [] (d/compartment-query
                        (d/db node) "Patient" "0" "Observation"
                        [["code" "system|code-2"]]))
        [0 :resourceType] := "Observation"
        [0 :id] := "1"
        1 := nil))))


(deftest instance-history
  (testing "a new node has a empty instance history"
    (let [db (d/db (new-node))]
      (is (d/ri-empty? (d/instance-history db "Patient" "0" nil nil)))
      (is (zero? (d/total-num-of-instance-changes db "Patient" "0" nil)))))

  (testing "a node with one resource shows it in the instance history"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (into [] (d/instance-history (d/db node) "Patient" "0" nil nil))
        [0 :resourceType] := "Patient"
        [0 :id] := "0"
        [0 :meta :versionId] := "1")
      (is (= 1 (d/total-num-of-instance-changes (d/db node) "Patient" "0" nil)))))

  (testing "a node with one deleted resource"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-instance-changes (d/db node) "Patient" "0" nil))))

      (testing "the first history entry is the deletion event"
        (given (into [] (d/instance-history (d/db node) "Patient" "0" nil nil))
          [0 meta :blaze.db/op] := :delete
          [0 :meta :versionId] := "2"))

      (testing "the second history entry is the creation event"
        (given (into [] (d/instance-history (d/db node) "Patient" "0" nil nil))
          [1 :resourceType] := "Patient"
          [1 :id] := "0"
          [1 :meta :versionId] := "1")))))


(deftest type-history
  (testing "a new node has a empty type history"
    (let [db (d/db (new-node))]
      (is (d/ri-empty? (d/type-history db "Patient" nil nil nil)))
      (is (zero? (d/total-num-of-type-changes db "Patient")))))

  (testing "a node with one resource shows it in the type history"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (into [] (d/type-history (d/db node) "Patient" nil nil nil))
        [0 :resourceType] := "Patient"
        [0 :id] := "0"
        [0 :meta :versionId] := "1")
      (is (= 1 (d/total-num-of-type-changes (d/db node) "Patient")))))

  (testing "a node with one deleted resource"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "the first history entry is the deletion event"
        (given (into [] (d/type-history (d/db node) "Patient" nil nil nil))
          [0 :id] := "0"
          [0 :meta :versionId] := "2"
          [0 meta :blaze.db/op] := :delete))

      (testing "the second history entry is the creation event"
        (given (into [] (d/type-history (d/db node) "Patient" nil nil nil))
          [1 :resourceType] := "Patient"
          [1 :id] := "0"
          [1 :meta :versionId] := "1"))))

  (testing "a node with two patients in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]
                          [:put {:resourceType "Patient" :id "1"}]])

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-type-changes (d/db node) "Patient"))))

      (testing "it is possible to start with the second patient"
        (given (into [] (d/type-history (d/db node) "Patient" 1 "1" nil))
          [0 :id] := "1")))))


(deftest system-history
  (testing "a new node has a empty system history"
    (let [db (d/db (new-node))]
      (is (d/ri-empty? (d/system-history db nil nil nil nil)))
      (is (zero? (d/total-num-of-system-changes db nil)))))

  (testing "a node with one resource shows it in the system history"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      (given (into [] (d/system-history (d/db node) nil nil nil nil))
        [0 :resourceType] := "Patient"
        [0 :id] := "0"
        [0 :meta :versionId] := "1")
      (is (= 1 (d/total-num-of-system-changes (d/db node) nil)))))

  (testing "a node with one deleted resource"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]])
      @(d/submit-tx node [[:delete "Patient" "0"]])

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-system-changes (d/db node) nil))))

      (testing "the first history entry is the deletion event"
        (given (into [] (d/system-history (d/db node) nil nil nil nil))
          [0 :id] := "0"
          [0 :meta :versionId] := "2"
          [0 meta :blaze.db/op] := :delete))

      (testing "the second history entry is the creation event"
        (given (into [] (d/system-history (d/db node) nil nil nil nil))
          [1 :resourceType] := "Patient"
          [1 :id] := "0"
          [1 :meta :versionId] := "1"))))

  (testing "a node with one patient and one observation in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]
                          [:put {:resourceType "Observation" :id "0"}]])

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-system-changes (d/db node) nil))))

      (testing "the observation comes first (due to type hashing)"
        (given (into [] (d/system-history (d/db node) nil nil nil nil))
          [0 :resourceType] := "Observation"
          [1 :resourceType] := "Patient"))

      (testing "it is possible to start with the patient"
        (given (into [] (d/system-history (d/db node) 1 "Patient" nil nil))
          [0 :resourceType] := "Patient"))))

  (testing "a node with two patients in one transaction"
    (let [node (new-node)]
      @(d/submit-tx node [[:put {:resourceType "Patient" :id "0"}]
                          [:put {:resourceType "Patient" :id "1"}]])

      (testing "has two history entries"
        (is (= 2 (d/total-num-of-system-changes (d/db node) nil))))

      (testing "it is possible to start with the second patient"
        (given (into [] (d/system-history (d/db node) 1 "Patient" "1" nil))
          [0 :id] := "1")))))

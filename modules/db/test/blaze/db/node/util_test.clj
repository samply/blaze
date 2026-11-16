(ns blaze.db.node.util-test
  (:require
   [blaze.db.node.util :as node-util]
   [blaze.db.node.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest node-name-test
  (testing "composite key"
    (is (= "main" (node-util/node-name [:blaze.db/node :blaze.db.main/node])))
    (is (= "admin" (node-util/node-name [:blaze.db/node :blaze.db.admin/node]))))

  (testing "plain key defaults to the main node"
    (is (= "main" (node-util/node-name :blaze.db/node)))))

(deftest index-bounds-test
  (testing "the chunk size is twice the pool size of the resource indexer
            executor and the look-ahead is four chunks"
    (doseq [[pool-size chunk-size look-ahead] [[1 2 8] [2 4 16] [4 8 32]]]
      (given (node-util/index-bounds pool-size)
        :chunk-size := chunk-size
        :look-ahead := look-ahead)))

  (testing "a chunk always fits into the look-ahead, so that the indexing loop
            can always dispatch"
    (doseq [pool-size [1 2 4]]
      (let [{:keys [chunk-size look-ahead]} (node-util/index-bounds pool-size)]
        (is (<= chunk-size look-ahead))))))

(deftest committed-tx-chunk-state-test
  (testing "drops what nothing needs after the commit of a transaction"
    (given (node-util/committed-tx-chunk-state
            {:tx-data {:t 1} :verified? true :entries [] :tx-stats {}
             :resource-handle-updates []})
      :verified? := true
      :tx-data := nil
      :entries := nil
      :tx-stats := nil
      :resource-handle-updates := nil)))

(defn- conj-entry [acc t x]
  (conj acc [t x]))

(deftest reduce-up-to-test
  (testing "reduces the entries with a t of at most t in the order of their t"
    (is (= [[1 :a] [2 :b]]
           (node-util/reduce-up-to conj-entry [] (sorted-map 3 :c 1 :a 2 :b) 2))))

  (testing "returns init if the t of no entry was reached"
    (is (= [] (node-util/reduce-up-to conj-entry [] (sorted-map 1 :a) 0))))

  (testing "without entries"
    (is (= [] (node-util/reduce-up-to conj-entry [] (sorted-map) 1)))))

(deftest remove-up-to-test
  (testing "removes the entries with a t of at most t"
    (is (= [3] (keys (node-util/remove-up-to (sorted-map 1 :a 2 :b 3 :c) 2)))))

  (testing "keeps the map sorted"
    (is (sorted? (node-util/remove-up-to (sorted-map 1 :a 2 :b) 1))))

  (testing "removes nothing if the t of no entry was reached"
    (is (= [1 2] (keys (node-util/remove-up-to (sorted-map 1 :a 2 :b) 0)))))

  (testing "removes all entries if the t of every entry was reached"
    (is (empty? (node-util/remove-up-to (sorted-map 1 :a 2 :b) 3))))

  (testing "without entries"
    (is (empty? (node-util/remove-up-to (sorted-map) 1)))))

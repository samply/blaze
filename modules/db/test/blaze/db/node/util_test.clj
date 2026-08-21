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

(ns blaze.db.node.util-test
  (:require
   [blaze.db.node.util :as node-util]
   [blaze.db.node.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest node-name-test
  (testing "composite key"
    (is (= "main" (node-util/node-name [:blaze.db/node :blaze.db.main/node])))
    (is (= "admin" (node-util/node-name [:blaze.db/node :blaze.db.admin/node]))))

  (testing "plain key defaults to the main node"
    (is (= "main" (node-util/node-name :blaze.db/node)))))

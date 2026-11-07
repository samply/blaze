(ns blaze.db.node.tx-indexer.util-test
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest rev-include-param-test
  (testing "holds the type, its tid and the c-hash of the code"
    (is (= ["Observation" (codec/tid "Observation") (codec/c-hash "subject")]
           (tx-u/rev-include-param "Observation" "subject")))))

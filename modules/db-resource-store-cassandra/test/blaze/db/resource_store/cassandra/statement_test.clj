(ns blaze.db.resource-store.cassandra.statement-test
  (:require
   [blaze.db.resource-store.cassandra.statement :as statement]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest are]])
  (:import
   [com.datastax.oss.driver.api.core ConsistencyLevel]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest put-statement-test
  (are [k v] (= v (.getConsistencyLevel (statement/put-statement k)))
    "ONE"
    ConsistencyLevel/ONE
    "TWO"
    ConsistencyLevel/TWO))

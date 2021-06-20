(ns blaze.db.resource-store.cassandra.statement-test
  (:require
    [blaze.db.resource-store.cassandra.statement :as statement]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest are]])
  (:import
    [com.datastax.oss.driver.api.core ConsistencyLevel]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest put-statement-test
  (are [k v] (= v (.getConsistencyLevel (statement/put-statement k)))
    "ONE"
    ConsistencyLevel/ONE
    "TWO"
    ConsistencyLevel/TWO))

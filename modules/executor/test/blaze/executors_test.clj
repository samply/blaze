(ns blaze.executors-test
  (:require
    [blaze.executors :as ex]
    [blaze.executors-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]))


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest single-thread-executor-test
  (is (ex/single-thread-executor)))

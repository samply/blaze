(ns blaze.db.hash-test
  (:require
    [blaze.db.hash :as hash]
    [clojure.test :as test :refer [deftest is testing]]))


(set! *warn-on-reflection* true)


(deftest generate
  (testing "bit length is 256"
    (is (= 256 (.bits (hash/generate {:resourceType "Patient" :id "0"}))))))

(ns blaze.db.impl.bytes-test
  (:require
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.bytes-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:refer-clojure :exclude [= concat]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest =
  (testing "first nil"
    (is (false? (bytes/= nil (byte-array 0)))))

  (testing "second nil"
    (is (false? (bytes/= (byte-array 0) nil)))))


(deftest concat
  (testing "no array"
    (is (bytes/= (byte-array []) (bytes/concat []))))

  (testing "one array"
    (is (bytes/= (byte-array [1]) (bytes/concat [(byte-array [1])]))))

  (testing "two arrays"
    (is (clojure.core/= [1 2] (vec (bytes/concat [(byte-array [1]) (byte-array [2])]))))))

(ns blaze.db.bytes-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.bytes-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(set! *warn-on-reflection* true)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest =-test
  (testing "nil is not the same as an empty array"
    (is (not (bytes/= nil bytes/empty)))
    (is (not (bytes/= bytes/empty nil))))

  (testing "nil equals nil"
    (is (bytes/= nil nil)))

  (testing "empty equals empty"
    (is (bytes/= bytes/empty bytes/empty))
    (is (bytes/= bytes/empty (byte-array 0)))
    (is (bytes/= (byte-array 0) bytes/empty)))

  (testing "equal arrays"
    (is (bytes/= (byte-array [0x11]) (byte-array [0x11]))))

  (testing "unequal arrays"
    (is (not (bytes/= (byte-array [0x10]) (byte-array [0x11]))))
    (is (not (bytes/= (byte-array [0x11]) (byte-array [0x10]))))))


(deftest empty-test
  (is (zero? (alength bytes/empty))))

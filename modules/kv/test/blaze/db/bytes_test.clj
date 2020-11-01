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


(deftest starts-with-test
  (testing "[0x00 0x01 0x02] starts with [0x00 0x01]"
    (is (bytes/starts-with? (byte-array [0x00 0x01 0x02])
                            (byte-array [0x00 0x01]))))

  (testing "[0x00 0x01 0x02] starts not with [0x00 0x01]"
    (is (not (bytes/starts-with? (byte-array [0x00 0x01 0x02])
                                 (byte-array [0x00 0x02])))))

  (testing "[0x00 0x01 0x02] starts not with [0x00 0x01 0x02 0x03]"
    (is (not (bytes/starts-with? (byte-array [0x00 0x01 0x02])
                                 (byte-array [0x00 0x01 0x02 0x03]))))))


(deftest <-test
  (testing "nil is lesser than the empty array"
    (is (bytes/< nil (byte-array 0))))

  (testing "[0x00] is lesser than [0x01]"
    (is (bytes/< (byte-array [0x00]) (byte-array [0x01]))))

  (testing "[0x00] is not lesser than [0x00]"
    (is (not (bytes/< (byte-array [0x00]) (byte-array [0x00])))))

  (testing "[0x00] is lesser than [0x00 0x00]"
    (is (bytes/< (byte-array [0x00]) (byte-array [0x00 0x00])))))


(deftest <=-test
  (testing "nil is lesser than or equal than the empty array"
    (is (bytes/<= nil (byte-array 0))))

  (testing "[0x00] is lesser than or equal than [0x01]"
    (is (bytes/<= (byte-array [0x00]) (byte-array [0x01]))))

  (testing "[0x00] is lesser than or equal than [0x00]"
    (is (bytes/<= (byte-array [0x00]) (byte-array [0x00]))))

  (testing "[0x01] is not lesser than or equal than [0x00]"
    (is (not (bytes/<= (byte-array [0x01]) (byte-array [0x00])))))

  (testing "[0x00] is lesser than or equal than [0x00 0x00]"
    (is (bytes/<= (byte-array [0x00]) (byte-array [0x00 0x00])))))


(deftest >-test
  (testing "the empty array is greater than nil"
    (is (bytes/> (byte-array 0) nil)))

  (testing "[0x01] is greater than [0x00]"
    (is (bytes/> (byte-array [0x01]) (byte-array [0x00]))))

  (testing "[0x00] is not greater than [0x00]"
    (is (not (bytes/> (byte-array [0x00]) (byte-array [0x00])))))

  (testing "[0x00 0x00] is greater than [0x00]"
    (is (bytes/> (byte-array [0x00 0x00]) (byte-array [0x00])))))


(deftest >=-test
  (testing "the empty array is greater than or equal nil"
    (is (bytes/>= (byte-array 0) nil)))

  (testing "[0x01] is greater than or equal [0x00]"
    (is (bytes/>= (byte-array [0x01]) (byte-array [0x00]))))

  (testing "[0x00] is greater than or equal [0x00]"
    (is (bytes/>= (byte-array [0x00]) (byte-array [0x00]))))

  (testing "[0x00] is not greater than or equal [0x01]"
    (is (not (bytes/>= (byte-array [0x00]) (byte-array [0x01])))))

  (testing "[0x00 0x00] is greater than or equal [0x00]"
    (is (bytes/>= (byte-array [0x00 0x00]) (byte-array [0x00])))))


(deftest empty-test
  (is (zero? (alength bytes/empty))))

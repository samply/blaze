(ns blaze.db.impl.bytes-test
  (:require
    [blaze.db.impl.bytes :as bytes]
    [blaze.db.impl.bytes-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:refer-clojure :exclude [=]))


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

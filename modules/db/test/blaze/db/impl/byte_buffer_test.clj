(ns blaze.db.impl.byte-buffer-test
  (:require
    [blaze.db.impl.byte-buffer :as bb]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest size-up-to-null-test
  (testing "empty buffer"
    (let [buf (bb/allocate 0)]
      (is (nil? (bb/size-up-to-null buf)))))

  (testing "buffer with only one null byte"
    (let [buf (bb/allocate 1)]
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (zero? (bb/size-up-to-null buf)))))

  (testing "buffer with only one non-null byte"
    (let [buf (bb/allocate 1)]
      (bb/put-byte! buf 1)
      (bb/flip! buf)
      (is (nil? (bb/size-up-to-null buf)))))

  (testing "buffer with one non-null and one null byte"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (= 1 (bb/size-up-to-null buf)))))

  (testing "buffer with two null bytes"
    (let [buf (bb/allocate 2)]
      (bb/put-byte! buf 0)
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (zero? (bb/size-up-to-null buf)))))

  (testing "buffer with two non-null and one null byte"
    (let [buf (bb/allocate 3)]
      (bb/put-byte! buf 1)
      (bb/put-byte! buf 2)
      (bb/put-byte! buf 0)
      (bb/flip! buf)
      (is (= 2 (bb/size-up-to-null buf))))))

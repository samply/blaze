(ns blaze.db.impl.index.t-by-instant-test
  (:require
    [blaze.db.impl.index.t-by-instant :as t-by-instant]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.test-util :as tu :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.time Instant]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(def system
  {::kv/mem {:column-families {:t-by-instant-index {:reverse-comparator? true}}}})


(deftest t-by-instant-test
  (testing "finds t directly at instant"
    (with-system [{kv-store ::kv/mem} system]
      (kv/put! kv-store [(t-by-instant/index-entry Instant/EPOCH 1)])

      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (= 1 (t-by-instant/t-by-instant snapshot Instant/EPOCH))))))

  (testing "finds t before instant"
    (with-system [{kv-store ::kv/mem} system]
      (kv/put! kv-store [(t-by-instant/index-entry Instant/EPOCH 1)])

      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (= 1 (t-by-instant/t-by-instant snapshot (.plusMillis Instant/EPOCH 1)))))))

  (testing "nothing is found on empty db"
    (with-system [{kv-store ::kv/mem} system]
      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (nil? (t-by-instant/t-by-instant snapshot Instant/EPOCH)))))))

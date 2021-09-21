(ns blaze.interaction.util-test
  (:require
    [blaze.interaction.util :as iu]
    [blaze.interaction.util-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest etag->t-test
  (testing "accepts nil"
    (is (nil? (iu/etag->t nil))))

  (testing "valid ETag"
    (is (= 1 (iu/etag->t "W/\"1\""))))

  (testing "invalid ETag"
    (are [s] (nil? (iu/etag->t s))
      "foo"
      "W/1"
      "W/\"a\"")))


(deftest clauses-test
  (testing "nil"
    (is (empty? (iu/clauses nil))))

  (testing "empty map"
    (is (empty? (iu/clauses {}))))

  (testing "empty key and value"
    (is (= [["" ""]] (iu/clauses {"" ""}))))

  (testing "empty key and two empty values"
    (is (= [["" ""] ["" ""]] (iu/clauses {"" ["" ""]}))))

  (testing "one key"
    (testing "and one value"
      (is (= [["a" "b"]] (iu/clauses {"a" "b"})))

      (testing "with two parts"
        (is (= [["a" "b" "c"]] (iu/clauses {"a" "b,c"}))))

      (testing "with three parts"
        (is (= [["a" "b" "c" "d"]] (iu/clauses {"a" "b,c,d"})))))

    (testing "and two values"
      (is (= [["a" "b"] ["a" "c"]] (iu/clauses {"a" ["b" "c"]}))))))

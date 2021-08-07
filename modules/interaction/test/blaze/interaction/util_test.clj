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

(ns blaze.async.flow-test
  (:require
    [blaze.async.flow :as flow]
    [blaze.async.flow-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.util.concurrent SubmissionPublisher]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest collect-test
  (testing "with publisher generating two numbers"
    (let [publisher (SubmissionPublisher.)
          future (flow/collect publisher)]
      (.submit publisher 1)
      (.submit publisher 2)
      (.close publisher)
      (is (= [1 2] @future))))

  (testing "with exceptionally closed publisher"
    (let [publisher (SubmissionPublisher.)
          future (flow/collect publisher)]
      (.submit publisher 1)
      (.closeExceptionally publisher (ex-info "e" {}))
      (try
        @future
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest mapcat-test
  (testing "with publisher generating two numbers"
    (let [publisher (SubmissionPublisher.)
          processor (flow/mapcat #(repeat % %))
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (.submit publisher 1)
      (.submit publisher 2)
      (.close publisher)
      (is (= [1 2 2] @future))))

  (testing "with exceptionally closed publisher"
    (let [publisher (SubmissionPublisher.)
          processor (flow/mapcat #(repeat % %))
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (.submit publisher 1)
      (.closeExceptionally publisher (ex-info "e" {}))
      (try
        @future
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))

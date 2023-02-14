(ns blaze.async.flow-test
  (:require
    [blaze.async.flow :as flow]
    [blaze.async.flow-spec]
    [blaze.test-util :as tu :refer [given-failed-future]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom])
  (:import
    [java.util.concurrent SubmissionPublisher]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest processor-test
  (is (flow/processor? (flow/mapcat #(repeat % %)))))


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
      (.closeExceptionally publisher (ex-info "e-102831" {}))

      (given-failed-future future
        ::anom/category := ::anom/fault
        ::anom/message := "e-102831"))))


(deftest mapcat-test
  (testing "with publisher generating one number"
    (let [publisher (SubmissionPublisher.)
          processor (flow/mapcat #(repeat % %))
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (.submit publisher 1)
      (.close publisher)
      (is (= [1] @future))))

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
      (.closeExceptionally publisher (ex-info "e-102426" {}))
      (given-failed-future future
        ::anom/category := ::anom/fault
        ::anom/message := "e-102426"))))

(deftest map-test
  (testing "with publisher generating one number"
    (let [publisher (SubmissionPublisher.)
          processor (flow/map inc)
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (.submit publisher 1)
      (.close publisher)
      (is (= [2] @future))))

  (testing "with publisher generating two numbers"
    (let [publisher (SubmissionPublisher.)
          processor (flow/map inc)
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (.submit publisher 1)
      (.submit publisher 2)
      (.close publisher)
      (is (= [2 3] @future))))

  (testing "with exceptionally closed publisher"
    (let [publisher (SubmissionPublisher.)
          processor (flow/map inc)
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (.submit publisher 1)
      (.closeExceptionally publisher (ex-info "e-102402" {}))
      (given-failed-future future
        ::anom/category := ::anom/fault
        ::anom/message := "e-102402"))))

(ns blaze.async.flow-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.async.flow :as flow]
   [blaze.async.flow-spec]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom])
  (:import
   [java.util.concurrent Flow$Subscriber Flow$Subscription
    SubmissionPublisher]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest processor-test
  (is (flow/processor? (flow/mapcat #(repeat % %)))))

(deftest collect-test
  (testing "with publisher generating two numbers"
    (let [publisher (SubmissionPublisher.)
          future (flow/collect publisher)]
      (flow/submit! publisher 1)
      (flow/submit! publisher 2)
      (.close publisher)
      (is (= [1 2] @future))))

  (testing "with exceptionally closed publisher"
    (let [publisher (SubmissionPublisher.)
          future (flow/collect publisher)]
      (flow/submit! publisher 1)
      (.closeExceptionally publisher (ex-info "e" {}))
      (try
        @future
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))

(deftest on-subscribe-test
  (testing "the subscriber receives the subscription"
    (let [subscription (reify Flow$Subscription
                         (request [_ _])
                         (cancel [_]))
          subscriptions (atom [])]
      (flow/on-subscribe!
       (reify Flow$Subscriber
         (onSubscribe [_ s] (swap! subscriptions conj s))
         (onNext [_ _])
         (onError [_ _])
         (onComplete [_]))
       subscription)

      (is (= [subscription] @subscriptions)))))

(deftest on-next-test
  (testing "the subscriber receives the item"
    (let [items (atom [])]
      (flow/on-next!
       (reify Flow$Subscriber
         (onSubscribe [_ _])
         (onNext [_ x] (swap! items conj x))
         (onError [_ _])
         (onComplete [_]))
       1)

      (is (= [1] @items)))))

(deftest on-error-test
  (testing "the subscriber receives the error"
    (let [future (ac/future)]
      (flow/on-error!
       (reify Flow$Subscriber
         (onSubscribe [_ _])
         (onNext [_ _])
         (onError [_ e] (ac/complete-exceptionally! future e))
         (onComplete [_]))
       (ba/ex-anom (ba/fault "msg-142837")))

      (given-failed-future future
        ::anom/category := ::anom/fault
        ::anom/message := "msg-142837"))))

(deftest on-complete-test
  (testing "the subscriber completes"
    (let [future (ac/future)]
      (flow/on-complete!
       (reify Flow$Subscriber
         (onSubscribe [_ _])
         (onNext [_ _])
         (onError [_ _])
         (onComplete [_] (ac/complete! future true))))

      (is (true? @future)))))

(deftest mapcat-test
  (testing "with publisher generating one number"
    (let [publisher (SubmissionPublisher.)
          processor (flow/mapcat #(repeat % %))
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (flow/submit! publisher 1)
      (.close publisher)
      (is (= [1] @future))))

  (testing "with publisher generating two numbers"
    (let [publisher (SubmissionPublisher.)
          processor (flow/mapcat #(repeat % %))
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (flow/submit! publisher 1)
      (flow/submit! publisher 2)
      (.close publisher)
      (is (= [1 2 2] @future))))

  (testing "with exceptionally closed publisher"
    (let [publisher (SubmissionPublisher.)
          processor (flow/mapcat #(repeat % %))
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (flow/submit! publisher 1)
      (.closeExceptionally publisher (ex-info "e" {}))
      (try
        @future
        (is false)
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))

(deftest take-test
  (testing "with publisher generating one number"
    (let [publisher (SubmissionPublisher.)
          processor (flow/take 1)
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (flow/submit! publisher 1)
      (.close publisher)

      (testing "that number is returned"
        (is (= [1] @future)))))

  (testing "with publisher generating two numbers"
    (testing "taking only one"
      (let [publisher (SubmissionPublisher.)
            processor (flow/take 1)
            future (flow/collect processor)]
        (flow/subscribe! publisher processor)
        (flow/submit! publisher 1)
        (flow/submit! publisher 2)
        (.close publisher)

        (testing "only one number is returned"
          (is (= [1] @future)))))

    (testing "taking both"
      (let [publisher (SubmissionPublisher.)
            processor (flow/take 2)
            future (flow/collect processor)]
        (flow/subscribe! publisher processor)
        (flow/submit! publisher 1)
        (flow/submit! publisher 2)
        (.close publisher)

        (testing "only one number is returned"
          (is (= [1 2] @future))))))

  (testing "with exceptionally closed publisher"
    (let [publisher (SubmissionPublisher.)
          processor (flow/take 1)
          future (flow/collect processor)]
      (flow/subscribe! publisher processor)
      (.closeExceptionally publisher (ex-info "e" {}))
      (try
        @future
        (is false)
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))

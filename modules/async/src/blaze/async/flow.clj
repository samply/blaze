(ns blaze.async.flow
  "This namespace provides functions to work with Java 9 Reactive Streams.

  https://www.baeldung.com/java-9-reactive-streams"
  (:require
    [blaze.async.comp :as ac])
  (:import
    [java.util.concurrent Flow$Processor Flow$Publisher Flow$Subscriber
                          Flow$Subscription SubmissionPublisher])
  (:refer-clojure :exclude [mapcat]))


(set! *warn-on-reflection* true)


(defn publisher? [x]
  (instance? Flow$Publisher x))


(defn processor? [x]
  (instance? Flow$Processor x))


(deftype Collector
  [xs ^:volatile-mutable future
   ^:volatile-mutable ^Flow$Subscription subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (.request subscription 1))
  (onNext [_ x]
    (.request subscription 1)
    (swap! xs conj x))
  (onError [_ e]
    (.cancel subscription)
    (ac/complete-exceptionally! future e))
  (onComplete [_]
    (ac/complete! future @xs)))


(defn- collector [future]
  (->Collector (atom []) future nil))


(defn collect
  "Returns a CompletableFuture that completes with a vector of all values
  `publisher` produces."
  [publisher]
  (let [future (ac/future)]
    (.subscribe ^Flow$Publisher publisher (collector future))
    future))


(defn mapcat
  "Returns a Processor which applies `f` to each value received assuming the
  result is a collection and produces the collection items individually."
  [f]
  (let [subscription (volatile! nil)]
    (proxy [SubmissionPublisher Flow$Processor] []
      (onSubscribe [s]
        (vreset! subscription s)
        (.request ^Flow$Subscription s 1))
      (onNext [x]
        (doseq [y (f x)]
          (.submit ^SubmissionPublisher this y))
        (.request ^Flow$Subscription @subscription 1))
      (onError [e]
        (.closeExceptionally ^SubmissionPublisher this e))
      (onComplete []
        (.close ^SubmissionPublisher this)))))

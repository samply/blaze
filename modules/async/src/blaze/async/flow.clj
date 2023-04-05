(ns blaze.async.flow
  "This namespace provides functions to work with Java 9 Reactive Streams.

  https://www.baeldung.com/java-9-reactive-streams"
  (:refer-clojure :exclude [map mapcat])
  (:require
    [blaze.async.comp :as ac]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Flow$Processor Flow$Publisher Flow$Subscriber
                          Flow$Subscription SubmissionPublisher]))


(set! *warn-on-reflection* true)


(defn publisher? [x]
  (instance? Flow$Publisher x))


(defn subscriber? [x]
  (instance? Flow$Subscriber x))


(defn subscription? [x]
  (instance? Flow$Subscription x))


(defn processor? [x]
  (instance? Flow$Processor x))


(defn subscribe! [publisher subscriber]
  (.subscribe ^Flow$Publisher publisher subscriber))


(defn on-subscribe! [subscriber subscription]
  (.onSubscribe ^Flow$Subscriber subscriber subscription))


(defn request! [subscription n]
  (.request ^Flow$Subscription subscription n))


(defn cancel! [subscription]
  (.cancel ^Flow$Subscription subscription))


(deftype Collector
  [xs ^:volatile-mutable future ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (request! subscription 1))
  (onNext [_ x]
    (request! subscription 1)
    (swap! xs conj x))
  (onError [_ e]
    (cancel! subscription)
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
    (subscribe! publisher (collector future))
    future))


(defn map
  "Returns a Processor which applies `f`."
  [f]
  (let [subscription (volatile! nil)]
    (proxy [SubmissionPublisher Flow$Processor] []
      (onSubscribe [s]
        (vreset! subscription s)
        (request! s 1))
      (onNext [x]
        (.submit ^SubmissionPublisher this (f x))
        (request! @subscription 1))
      (onError [e]
        (log/error "on error" e)
        (.closeExceptionally ^SubmissionPublisher this e))
      (onComplete []
        (log/trace "on complete")
        (.close ^SubmissionPublisher this)))))


(defn mapcat
  "Returns a Processor which applies `f` to each value received assuming the
  result is a collection and produces the collection items individually."
  [f]
  (let [subscription (volatile! nil)]
    (proxy [SubmissionPublisher Flow$Processor] []
      (onSubscribe [s]
        (vreset! subscription s)
        (request! s 1))
      (onNext [x]
        (run! #(.submit ^SubmissionPublisher this %) (f x))
        (request! @subscription 1))
      (onError [e]
        (log/error "on error" e)
        (.closeExceptionally ^SubmissionPublisher this e))
      (onComplete []
        (log/trace "on complete")
        (.close ^SubmissionPublisher this)))))


(defn async-map
  "Returns a Processor which applies `f` expecting an asynchronous result."
  [f]
  (let [subscription (volatile! nil)]
    (proxy [SubmissionPublisher Flow$Processor] []
      (onSubscribe [s]
        (vreset! subscription s)
        (request! s 1))
      (onNext [x]
        (-> (f x)
            (ac/then-apply
              (fn [y]
                (.submit ^SubmissionPublisher this y)
                (request! @subscription 1)))
            (ac/exceptionally
              (fn [e]
                (log/error "exceptionally" e)
                (.closeExceptionally ^SubmissionPublisher this e)))))
      (onError [e]
        (log/error "on error" e)
        (.closeExceptionally ^SubmissionPublisher this e))
      (onComplete []
        (log/trace "on complete")
        (.close ^SubmissionPublisher this)))))

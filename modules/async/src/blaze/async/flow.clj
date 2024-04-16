(ns blaze.async.flow
  "This namespace provides functions to work with Java 9 Reactive Streams.

  https://www.baeldung.com/java-9-reactive-streams"
  (:refer-clojure :exclude [mapcat take])
  (:require
   [blaze.async.comp :as ac])
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

(defn subscribe!
  "Adds `subscriber` to the list of subscribers of `publisher`."
  [publisher subscriber]
  (.subscribe ^Flow$Publisher publisher subscriber))

(defn on-subscribe! [subscriber subscription]
  (.onSubscribe ^Flow$Subscriber subscriber subscription))

(defn request! [subscription n]
  (.request ^Flow$Subscription subscription n))

(defn cancel! [subscription]
  (.cancel ^Flow$Subscription subscription))

(defn submit! [submission-publisher x]
  (.submit ^SubmissionPublisher submission-publisher x))

(deftype Collector
         [xs ^:volatile-mutable future ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (request! subscription 1))
  (onNext [_ x]
    (swap! xs conj x)
    (request! subscription 1))
  (onError [_ e]
    (cancel! subscription)
    (ac/complete-exceptionally! future e))
  (onComplete [_]
    (ac/complete! future @xs)))

(defn- collector [future]
  (->Collector (atom []) future nil))

(defn collect
  "Returns a CompletableFuture that will complete with a vector of all values
  `publisher` produces."
  [publisher]
  (let [future (ac/future)]
    (subscribe! publisher (collector future))
    future))

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
        (run! (partial submit! this) (f x))
        (request! @subscription 1))
      (onError [e]
        (.closeExceptionally ^SubmissionPublisher this e))
      (onComplete []
        (.close ^SubmissionPublisher this)))))

(defn take [n]
  (let [subscription (volatile! nil)
        remaining (volatile! n)]
    (proxy [SubmissionPublisher Flow$Processor] []
      (onSubscribe [s]
        (vreset! subscription s)
        (request! s 1))
      (onNext [x]
        (submit! this x)
        (if (zero? (vswap! remaining dec))
          (.close ^SubmissionPublisher this)
          (request! @subscription 1)))
      (onError [e]
        (.closeExceptionally ^SubmissionPublisher this e))
      (onComplete []
        (.close ^SubmissionPublisher this)))))

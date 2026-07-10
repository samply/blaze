(ns blaze.validator.extern.semaphore
  "A simple asynchronous, non-blocking counting semaphore built on
  CompletableFuture.

  Unlike java.util.concurrent.Semaphore, acquiring a permit never blocks a
  thread. Instead `acquire!` returns a CompletableFuture that completes as soon
  as a permit becomes available. This allows bounding the number of concurrent
  asynchronous operations (like outbound HTTP requests) without tying up threads
  while waiting for a permit."
  (:require
   [blaze.async.comp :as ac])
  (:import
   [clojure.lang PersistentQueue]))

(set! *warn-on-reflection* true)

(defn semaphore
  "Creates an asynchronous semaphore with `permits` initially available
  permits."
  [permits]
  (atom {:permits permits :waiters PersistentQueue/EMPTY}))

(defn acquire!
  "Acquires a permit from `semaphore`.

  Returns a CompletableFuture that completes with nil as soon as a permit is
  available. Every acquired permit has to be returned with `release!`."
  [semaphore]
  (let [future (ac/future)
        [{:keys [permits]} _]
        (swap-vals! semaphore
                    (fn [s]
                      (if (pos? (:permits s))
                        (update s :permits dec)
                        (update s :waiters conj future))))]
    (when (pos? permits)
      (ac/complete! future nil))
    future))

(defn release!
  "Returns a permit to `semaphore`.

  If there is a pending `acquire!`, its CompletableFuture is completed instead
  of incrementing the number of available permits.

  There is no requirement that a thread that releases a permit must have
  acquired that permit by calling `acquire!`. Correct usage of a semaphore is
  established by programming convention in the application."
  [semaphore]
  (let [[{:keys [waiters]} _]
        (swap-vals! semaphore
                    (fn [s]
                      (if (seq (:waiters s))
                        (update s :waiters pop)
                        (update s :permits inc))))]
    (when-some [future (peek waiters)]
      (ac/complete! future nil))))

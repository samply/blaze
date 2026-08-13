(ns blaze.db.node.subscription-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [blaze.async.flow-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.node.subscription :as sub]
   [blaze.db.node.subscription-spec]
   [blaze.db.test-util :refer [wait-for]]
   [blaze.fhir.hash :as hash]
   [blaze.test-util :as tu :refer [given-failed-future with-global-log-capture]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import
   [blaze.db.impl.index ResourceHandle]
   [java.util.concurrent Flow$Subscriber Flow$Subscription]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- config
  "Returns a subscription config, recording the removals in `removed` and the
  wake-ups of the publishing loop in `wake-ups`."
  [{:keys [queue-capacity queued-t removed wake-ups]
    :or {queue-capacity 2 queued-t 0}}]
  {:type "Task"
   :name "test"
   :thread-name "test-changed-Task-resources-subscriber"
   :queue-capacity queue-capacity
   :queued-t queued-t
   :remove! (fn [subscription] (some-> removed (swap! conj subscription)))
   :wake-node-publish-loop! (fn [] (some-> wake-ups (swap! inc)))})

(defn- resource-handle [id t]
  (ResourceHandle. :fhir/Task (codec/tid "Task") id t
                   (hash/generate {:fhir/type :fhir/Task :id id}) 0 nil))

(defn- handles
  "Returns the resource handles of the transaction with `t`, one per `id`."
  [t & ids]
  (mapv #(resource-handle % t) ids))

(defn- changed-handles
  "Returns the `handles` in descending transaction order, like the publishing
  loop determines them."
  [& handles]
  (vec (reverse handles)))

(defn- collecting-subscriber
  "Returns a subscriber that requests one item at a time, conjoining the
  received items onto `items` and completing `future` after it received
  onComplete or onError."
  [items future]
  (let [subscription (volatile! nil)]
    (reify Flow$Subscriber
      (onSubscribe [_ s]
        (vreset! subscription s)
        (flow/request! s 1))
      (onNext [_ x]
        (swap! items conj x)
        (flow/request! @subscription 1))
      (onError [_ e]
        (ac/complete-exceptionally! future e))
      (onComplete [_]
        (ac/complete! future true)))))

(defn- demanding-subscriber
  "Returns a subscriber that requests nothing on its own, delivering its
  `subscription` in onSubscribe so that the demand can be created later,
  conjoining the received items onto `items` and completing `future` after it
  received onComplete or onError."
  [subscription items future]
  (reify Flow$Subscriber
    (onSubscribe [_ s] (deliver subscription s))
    (onNext [_ x] (swap! items conj x))
    (onError [_ e] (ac/complete-exceptionally! future e))
    (onComplete [_] (ac/complete! future true))))

(defn- non-requesting-subscriber
  "Returns a subscriber that never requests anything, so that the queue of its
  subscription stays full, conjoining the items it receives nonetheless onto
  `items` and completing `future` after it received onComplete or onError."
  ([]
   (non-requesting-subscriber (atom []) (ac/future)))
  ([items future]
   (demanding-subscriber (promise) items future)))

(defn- request!
  "Requests `n` items from the `subscription` a `demanding-subscriber` received
  in onSubscribe."
  [subscription n]
  (flow/request! (deref subscription 10000 nil) n))

(def ^:private slow-subscriber-msg
  "The changed Task resources subscriber test doesn't consume fast enough. Transactions are still indexed, but its publishing lag will grow.")

(deftest queued-t-test
  (testing "the initial queued-t is the one of the config"
    (is (= 5 (sub/queued-t (sub/subscription (config {:queued-t 5}) (collecting-subscriber (atom []) (ac/future))))))))

(deftest window-t-test
  (testing "the window of a subscription with transactions to examine ends at t"
    (let [subscription (sub/subscription (config {}) (collecting-subscriber (atom []) (ac/future)))]
      (is (= 1 (sub/window-t subscription 1)))))

  (testing "the window is bounded by the free slots of the queue"
    ;; the handles of the transactions that don't fit into the queue anymore
    ;; can't be queued in this round, so they aren't determined either
    (let [subscription (sub/subscription (config {:queue-capacity 2})
                                         (non-requesting-subscriber))]
      (is (= 2 (sub/window-t subscription 5)))

      (testing "and shrinks with the space left"
        (sub/publish! subscription 2 (changed-handles (handles 1 "0")))

        (is (= 3 (sub/window-t subscription 5))))))

  (testing "a subscription that examined all transactions has no window"
    (let [subscription (sub/subscription (config {:queued-t 1}) (collecting-subscriber (atom []) (ac/future)))]
      (is (nil? (sub/window-t subscription 1)))))

  (testing "a subscription with a full queue has no window"
    (with-global-log-capture [captured slow-subscriber-msg]
      (let [subscription (sub/subscription (config {:queue-capacity 1})
                                           (non-requesting-subscriber))]
        (sub/publish! subscription 1 (changed-handles (handles 1 "0")))

        (is (nil? (sub/window-t subscription 2)))

        (testing "and its subscriber is logged as slow"
          (given (deref captured 10000 ::timeout)
            :level := :warn)))))

  (testing "a subscription with a full queue that examined all transactions isn't logged as slow"
    ;; its subscriber didn't fall behind, there is simply nothing left to
    ;; publish to it
    (with-global-log-capture [captured slow-subscriber-msg]
      (let [subscription (sub/subscription (config {:queue-capacity 1})
                                           (non-requesting-subscriber))]
        (sub/publish! subscription 1 (changed-handles (handles 1 "0")))

        (is (nil? (sub/window-t subscription 1)))

        (is (nil? (deref captured 100 nil)))))))

(deftest wake-up-test
  (testing "the publishing loop is woken up after the queue drained to half its capacity"
    ;; waking it up at the first free slot would make it run a round per
    ;; delivered transaction, each with its own batch database
    (let [wake-ups (atom 0)
          items (atom [])
          subscriber-subscription (promise)
          subscription (sub/subscription
                        (config {:queue-capacity 4 :wake-ups wake-ups})
                        (demanding-subscriber subscriber-subscription items (ac/future)))]
      (sub/publish! subscription 4 (changed-handles (handles 1 "0") (handles 2 "1")
                                                    (handles 3 "2") (handles 4 "3")))

      ;; the publishing loop is woken up before the delivery, so a wake-up is
      ;; already visible when the delivery it belongs to was observed
      (testing "the first delivery doesn't wake it up"
        (request! subscriber-subscription 1)
        (is (true? (wait-for items #(= 1 (count %)))))
        (is (zero? @wake-ups)))

      (testing "the second delivery wakes it up"
        (request! subscriber-subscription 1)
        (is (true? (wait-for items #(= 2 (count %)))))
        (is (= 1 @wake-ups)))

      (testing "the third delivery doesn't wake it up again"
        (request! subscriber-subscription 1)
        (is (true? (wait-for items #(= 3 (count %)))))
        (is (= 1 @wake-ups))))))

(deftest ended?-test
  (testing "a fresh subscription didn't end"
    (is (false? (sub/ended? (sub/subscription (config {}) (collecting-subscriber (atom []) (ac/future)))))))

  (testing "a subscription whose subscriber cancelled ended"
    (with-global-log-capture [_ "Remove the changed Task resources subscriber test because it cancelled its subscription."]
      (let [s (sub/subscription
               (config {})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (flow/cancel! s))
                 (onNext [_ _])
                 (onError [_ _])
                 (onComplete [_])))]
        (is (true? (sub/ended? s))))))

  (testing "a closed subscription ended"
    (let [s (sub/subscription (config {}) (collecting-subscriber (atom []) (ac/future)))]
      (sub/close! s 0)

      (is (true? (sub/ended? s))))))

(deftest publish-test
  (testing "with a subscriber that consumes"
    (let [items (atom [])
          future (ac/future)
          subscription (sub/subscription (config {}) (collecting-subscriber items future))]

      (sub/publish! subscription 2 (changed-handles (handles 1 "0") (handles 2 "1")))

      (testing "the queued-t is advanced to the t of the round"
        (is (= 2 (sub/queued-t subscription))))

      (testing "the subscriber receives one item per transaction, in order"
        (is (true? (wait-for items #(= 2 (count %)))))
        (given @items
          [0 0 :id] := "0"
          [1 0 :id] := "1"))

      (sub/close! subscription 2)
      (is (true? (deref future 10000 ::timeout)))))

  (testing "transactions up to the queued-t are skipped"
    (let [items (atom [])
          future (ac/future)
          subscription (sub/subscription (config {:queued-t 1}) (collecting-subscriber items future))]

      (sub/publish! subscription 2 (changed-handles (handles 1 "0") (handles 2 "1")))

      (testing "only the transaction after the queued-t is published"
        (is (true? (wait-for items #(= 1 (count %)))))
        (given @items
          [0 0 :id] := "1"))))

  (testing "transactions after t are skipped"
    ;; the changed handles of a type are determined once for all of its
    ;; subscriptions, so they can reach beyond the window of a single one
    (let [items (atom [])
          future (ac/future)
          subscription (sub/subscription (config {}) (collecting-subscriber items future))]

      (sub/publish! subscription 1 (changed-handles (handles 1 "0") (handles 2 "1")))

      (testing "the queued-t is only advanced to the end of the window"
        (is (= 1 (sub/queued-t subscription))))

      (testing "only the transaction of the window is published"
        (is (true? (wait-for items #(= 1 (count %)))))
        (given @items
          [0 0 :id] := "0"))

      (testing "the transaction after the window is published in the next round"
        (sub/publish! subscription 2 (changed-handles (handles 1 "0") (handles 2 "1")))

        (is (true? (wait-for items #(= 2 (count %)))))
        (given @items
          [1 0 :id] := "1"))))

  (testing "without changed handles, the queued-t is still advanced"
    (let [subscription (sub/subscription (config {}) (collecting-subscriber (atom []) (ac/future)))]

      (sub/publish! subscription 3 [])
      (is (= 3 (sub/queued-t subscription)))))

  (testing "with a subscriber that doesn't consume"
    (with-global-log-capture [captured slow-subscriber-msg]
      (let [wake-ups (atom 0)
            items (atom [])
            future (ac/future)
            subscriber-subscription (promise)
            subscription (sub/subscription
                          (config {:queue-capacity 2 :wake-ups wake-ups})
                          (demanding-subscriber subscriber-subscription items future))]

        (testing "the window of the round is bounded by the queue capacity"
          (is (= 2 (sub/window-t subscription 3))))

        (sub/publish! subscription 2 (changed-handles (handles 1 "0") (handles 2 "1")))

        (testing "the queued-t is advanced to the end of the window"
          (is (= 2 (sub/queued-t subscription))))

        (testing "the next round finds the queue full"
          (is (nil? (sub/window-t subscription 3))))

        (testing "the slow subscriber is logged"
          (given (deref captured 10000 ::timeout)
            :level := :warn))

        (testing "the queued transactions and the distance to the t of the node are exposed separately"
          (is (= 2 (sub/queued subscription)))
          (is (= 2 (sub/unexamined subscription 4))))

        (testing "the publishing loop is woken up after the subscriber consumed"
          (request! subscriber-subscription 2)
          (is (true? (wait-for wake-ups pos?))))

        (testing "the subscriber receives the queued handles"
          (is (true? (wait-for items #(= 2 (count %)))))))))

  (testing "with a subscriber that consumes slower than the publisher"
    ;; the subscriber consumes, so the publishing loop finds space again after
    ;; every delivery and runs into the full queue over and over. Its lag phase
    ;; lasts until it consumed everything queued.
    (let [items (atom [])
          subscriber-subscription (promise)
          subscription (sub/subscription
                        (config {:queue-capacity 2})
                        (demanding-subscriber subscriber-subscription items (ac/future)))]

      (testing "the slow subscriber is logged when its queue is full"
        (with-global-log-capture [captured slow-subscriber-msg]
          (sub/publish! subscription 2 (changed-handles (handles 1 "0") (handles 2 "1")))

          (is (nil? (sub/window-t subscription 3)))

          (given (deref captured 10000 ::timeout)
            :level := :warn)))

      (testing "the slow subscriber isn't logged again after it consumed one transaction"
        (request! subscriber-subscription 1)
        (is (true? (wait-for items #(= 1 (count %)))))

        (with-global-log-capture [captured slow-subscriber-msg]
          (testing "the free slot bounds the window of the next round"
            (is (= 3 (sub/window-t subscription 4))))

          (sub/publish! subscription 3 (changed-handles (handles 3 "2")))

          (is (nil? (sub/window-t subscription 4)))

          (is (= ::not-logged (deref captured 100 ::not-logged)))))

      (testing "the slow subscriber is logged again in the next lag phase"
        (testing "after its queue ran empty"
          (request! subscriber-subscription 2)
          (is (true? (wait-for items #(= 3 (count %))))))

        (with-global-log-capture [captured slow-subscriber-msg]
          (is (= 5 (sub/window-t subscription 6)))

          (sub/publish! subscription 5 (changed-handles (handles 4 "3") (handles 5 "4")))

          (is (nil? (sub/window-t subscription 6)))

          (given (deref captured 10000 ::timeout)
            :level := :warn)))))

  (testing "with a round that exactly fills the queue"
    ;; the publishing loop leaves a subscription with a full queue out of a
    ;; round, even if the last round didn't run out of capacity. So the queue
    ;; running full is what the publishing loop has to be woken up for, not the
    ;; round being cut short.
    (let [wake-ups (atom 0)
          items (atom [])
          subscriber-subscription (promise)
          subscription (sub/subscription
                        (config {:queue-capacity 1 :wake-ups wake-ups})
                        (demanding-subscriber subscriber-subscription items (ac/future)))]

      (testing "the round is complete, but the queue is full"
        (sub/publish! subscription 1 (changed-handles (handles 1 "0")))
        (is (= 1 (sub/queued-t subscription))))

      (testing "the publishing loop is woken up after the subscriber consumed"
        (request! subscriber-subscription 1)
        (is (true? (wait-for items #(= 1 (count %)))))
        (is (true? (wait-for wake-ups pos?))))))

  (testing "the queue is filled again after the subscriber consumed"
    (let [items (atom [])
          future (ac/future)
          changed-handles (changed-handles (handles 1 "0") (handles 2 "1"))
          subscriber-subscription (promise)
          subscription (sub/subscription
                        (config {:queue-capacity 1})
                        (demanding-subscriber subscriber-subscription items future))]

      (testing "only the first transaction fits into the window"
        (is (= 1 (sub/window-t subscription 2)))

        (sub/publish! subscription 1 changed-handles)
        (is (= 1 (sub/queued-t subscription))))

      (request! subscriber-subscription 1)
      (is (true? (wait-for items #(= 1 (count %)))))

      (testing "the second transaction fits after the subscriber consumed"
        (is (= 2 (sub/window-t subscription 2)))

        (sub/publish! subscription 2 changed-handles)
        (is (= 2 (sub/queued-t subscription))))

      (testing "the subscriber doesn't receive the first transaction twice"
        (request! subscriber-subscription 1)
        (is (true? (wait-for items #(= 2 (count %)))))
        (given @items
          [0 0 :id] := "0"
          [1 0 :id] := "1")))))

(deftest queued-test
  (testing "a subscription without queued handles has nothing queued"
    (let [subscription (sub/subscription (config {}) (collecting-subscriber (atom []) (ac/future)))]
      (is (= 0 (sub/queued subscription)))))

  (testing "every queued resource handle vector is one transaction to deliver"
    ;; unlike the distance in t, this counts the transactions that actually
    ;; changed resources of the type, because only those occupy a slot
    (let [subscription (sub/subscription (config {}) (non-requesting-subscriber))]
      (sub/publish! subscription 5 (changed-handles (handles 2 "0") (handles 5 "1")))

      (is (= 2 (sub/queued subscription)))))

  (testing "transactions that changed no resource of the type queue nothing"
    (let [subscription (sub/subscription (config {}) (non-requesting-subscriber))]
      (sub/publish! subscription 5 [])

      (is (= 0 (sub/queued subscription))))))

(deftest unexamined-test
  (testing "a subscription that examined all transactions has no distance"
    (let [subscription (sub/subscription (config {:queued-t 3}) (collecting-subscriber (atom []) (ac/future)))]
      (is (= 0 (sub/unexamined subscription 3)))))

  (testing "the t's after the queued-t up to and including t are the distance"
    (let [subscription (sub/subscription (config {:queued-t 3}) (collecting-subscriber (atom []) (ac/future)))]
      (is (= 2 (sub/unexamined subscription 5)))))

  (testing "a subscription that already examined transactions after t has no distance"
    ;; the t comes from the state of the node, which is read before the state of
    ;; the subscription, so the subscription can have advanced past it meanwhile
    (let [subscription (sub/subscription (config {:queued-t 5}) (collecting-subscriber (atom []) (ac/future)))]
      (is (= 0 (sub/unexamined subscription 3))))))

(deftest subscription-test
  (testing "the subscriber receives onSubscribe"
    (let [subscription (promise)
          s (sub/subscription
             (config {})
             (reify Flow$Subscriber
               (onSubscribe [_ s] (deliver subscription s))
               (onNext [_ _])
               (onError [_ _])
               (onComplete [_])))]
      (is (identical? s (deref subscription 10000 nil)))))

  (testing "a subscriber that cancels in onSubscribe is removed"
    (with-global-log-capture [captured "Remove the changed Task resources subscriber test because it cancelled its subscription."]
      (let [removed (atom [])
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (flow/cancel! s))
                 (onNext [_ _])
                 (onError [_ _])
                 (onComplete [_])))]
        (is (= [s] @removed))

        (given (deref captured 10000 ::timeout)
          :level := :warn))))

  (testing "a subscriber throwing in onSubscribe doesn't receive any other signal"
    ;; the reactive streams spec considers the subscription of a subscriber that
    ;; throws in onSubscribe as cancelled
    (let [subscription (promise)
          signalled (promise)
          subscriber (reify Flow$Subscriber
                       (onSubscribe [_ s]
                         (deliver subscription s)
                         (throw (Exception. "msg-125621")))
                       (onNext [_ _])
                       (onError [_ _] (deliver signalled true))
                       (onComplete [_] (deliver signalled true)))]
      (testing "and the error is rethrown, so that the component subscribing doesn't run with a subscription that never delivers"
        (is (thrown-with-msg? Exception #"msg-125621"
                              (sub/subscription (config {}) subscriber))))

      (testing "and the thread of its drain-loop exits"
        ;; bounded, because waiting would block forever on a regression
        (is (true? (deref (future (sub/wait-until-finished! (deref subscription 10000 nil)))
                          10000 ::timeout))))

      (is (nil? (deref signalled 0 nil))))))

(deftest wait-until-finished-test
  (testing "waiting returns after the thread of the subscription exited"
    (let [completed (promise)
          s (sub/subscription
             (config {})
             (reify Flow$Subscriber
               (onSubscribe [_ s] (flow/request! s 1))
               (onNext [_ _])
               (onError [_ _])
               (onComplete [_] (deliver completed true))))]
      (sub/close! s 0)
      (sub/wait-until-finished! s)

      (testing "the subscriber received onComplete already"
        (is (true? (deref completed 0 nil))))))

  (testing "waiting returns for a delivery in progress only after it finished"
    ;; the node closes its subscriptions and waits for their threads afterwards,
    ;; so that no subscriber code runs against it anymore
    (let [delivering (promise)
          release (promise)
          delivered (promise)
          s (sub/subscription
             (config {})
             (reify Flow$Subscriber
               (onSubscribe [_ s] (flow/request! s 1))
               (onNext [_ _]
                 (deliver delivering true)
                 @release
                 (deliver delivered true))
               (onError [_ _])
               (onComplete [_])))]
      (sub/publish! s 1 (changed-handles (handles 1 "0")))
      (is (true? (deref delivering 10000 nil)))

      (sub/close! s 1)
      (deliver release true)
      (sub/wait-until-finished! s)

      (is (true? (deref delivered 0 nil)))))

  (testing "waiting returns for a subscription whose subscriber cancelled in onSubscribe"
    ;; the thread of its drain-loop exits without signalling anything, so
    ;; waiting would block forever on a regression
    (with-global-log-capture [_ "Remove the changed Task resources subscriber test because it cancelled its subscription."]
      (let [removed (atom [])
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (flow/cancel! s))
                 (onNext [_ _])
                 (onError [_ _])
                 (onComplete [_])))]
        (is (true? (deref (future (sub/wait-until-finished! s)) 10000 ::timeout)))

        (is (= [s] @removed))))))

(deftest cancel-test
  (testing "a cancelled subscriber doesn't receive handles anymore"
    (with-global-log-capture [_ "Remove the changed Task resources subscriber test because it cancelled its subscription."]
      (let [removed (atom [])
            items (atom [])
            subscription (volatile! nil)
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (vreset! subscription s) (flow/request! s 10))
                 (onNext [_ x] (swap! items conj x) (flow/cancel! @subscription))
                 (onError [_ _])
                 (onComplete [_])))]
        (sub/publish! s 2 (changed-handles (handles 1 "0") (handles 2 "1")))

        (is (true? (wait-for removed #(= [s] %))))

        (testing "only the handles of the first transaction are received"
          (is (= 1 (count @items))))

        (testing "closing a cancelled subscription doesn't signal anything"
          (sub/close! s 2)
          (is (= 1 (count @items)))))))

  (testing "a subscriber cancelling after onComplete isn't reported as dropped"
    (let [removed (atom [])
          completed (promise)
          subscription (volatile! nil)
          s (sub/subscription
             (config {:removed removed})
             (reify Flow$Subscriber
               (onSubscribe [_ s] (vreset! subscription s) (flow/request! s 1))
               (onNext [_ _])
               (onError [_ _])
               (onComplete [_]
                 (flow/cancel! @subscription)
                 (deliver completed true))))]
      (sub/close! s 0)

      (is (true? (deref completed 10000 nil)))
      (is (= [] @removed))))

  (testing "cancelling twice removes the subscription only once"
    (with-global-log-capture [_ "Remove the changed Task resources subscriber test because it cancelled its subscription."]
      (let [removed (atom [])
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (flow/cancel! s) (flow/cancel! s))
                 (onNext [_ _])
                 (onError [_ _])
                 (onComplete [_])))]
        (is (= [s] @removed))))))

(deftest request-test
  (testing "a non-positive request ends the subscription with an error"
    (with-global-log-capture [captured "Remove the changed Task resources subscriber test because of: Non-positive request of 0 items."]
      (let [removed (atom [])
            future (ac/future)
            subscription (volatile! nil)
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (vreset! subscription s))
                 (onNext [_ _])
                 (onError [_ e] (ac/complete-exceptionally! future e))
                 (onComplete [_])))]
        (.request ^Flow$Subscription @subscription 0)

        (given-failed-future future
          ::anom/category := ::anom/incorrect
          ::anom/message := "Non-positive request of 0 items.")

        (testing "the subscription is removed, so nothing is published to it anymore"
          (is (= [s] @removed)))

        (given (deref captured 10000 ::timeout)
          :level := :warn))))

  (testing "a non-positive request ends the subscription without delivering the queued handles"
    ;; the reactive streams spec expects the onError signal to be sent promptly,
    ;; not only after the queue was drained
    (with-global-log-capture [_ "Remove the changed Task resources subscriber test because of: Non-positive request of 0 items."]
      (let [items (atom [])
            future (ac/future)
            subscription (volatile! nil)
            s (sub/subscription
               (config {:queue-capacity 3})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (vreset! subscription s) (flow/request! s 10))
                 (onNext [_ x]
                   (swap! items conj x)
                   (.request ^Flow$Subscription @subscription 0))
                 (onError [_ e] (ac/complete-exceptionally! future e))
                 (onComplete [_])))]
        (sub/publish! s 3 (changed-handles (handles 1 "0") (handles 2 "1")
                                           (handles 3 "2")))
        (given-failed-future future
          ::anom/category := ::anom/incorrect
          ::anom/message := "Non-positive request of 0 items.")

        (testing "only the handles of the first transaction are received"
          (is (= 1 (count @items)))))))

  (testing "a second non-positive request removes the subscription only once"
    (with-global-log-capture [_ "Remove the changed Task resources subscriber test because of: Non-positive request of 0 items."]
      (let [removed (atom [])
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s]
                   (.request ^Flow$Subscription s 0)
                   (.request ^Flow$Subscription s 0))
                 (onNext [_ _])
                 (onError [_ _])
                 (onComplete [_])))]
        (is (= [s] @removed)))))

  (testing "an unbounded request twice doesn't overflow the demand"
    (let [items (atom [])
          future (ac/future)
          s (sub/subscription
             (config {})
             (reify Flow$Subscriber
               (onSubscribe [_ s]
                 (.request ^Flow$Subscription s Long/MAX_VALUE)
                 (.request ^Flow$Subscription s Long/MAX_VALUE))
               (onNext [_ x] (swap! items conj x))
               (onError [_ e] (ac/complete-exceptionally! future e))
               (onComplete [_] (ac/complete! future true))))]
      (sub/publish! s 2 (changed-handles (handles 1 "0") (handles 2 "1")))

      (testing "the subscriber still receives the handles"
        (is (true? (wait-for items #(= 2 (count %))))))

      (sub/close! s 2)
      (is (true? (deref future 10000 ::timeout)))))

  (testing "cancelling after a non-positive request doesn't remove the subscription again"
    (with-global-log-capture [_ "Remove the changed Task resources subscriber test because of: Non-positive request of 0 items."]
      (let [removed (atom [])
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s]
                   (.request ^Flow$Subscription s 0)
                   (flow/cancel! s))
                 (onNext [_ _])
                 (onError [_ _])
                 (onComplete [_])))]
        (is (= [s] @removed))))))

(deftest close-test
  (testing "the queued handles are dropped"
    ;; delivering them would run subscriber code after the node was closed,
    ;; which would query or even transact on a node that is already shutting
    ;; down
    (with-global-log-capture [captured "Dropped the changed Task resources of 1 transaction(s) for the subscriber test because the node was closed before it consumed them."]
      (let [items (atom [])
            future (ac/future)
            s (sub/subscription (config {}) (non-requesting-subscriber items future))]
        (sub/publish! s 1 (changed-handles (handles 1 "0")))
        (sub/close! s 1)

        (testing "the subscriber completes without receiving them"
          (is (true? (deref future 10000 ::timeout)))
          (is (= [] @items)))

        (testing "the dropped transaction is logged"
          (given (deref captured 10000 ::timeout)
            :level := :warn)))))

  (testing "a subscriber that doesn't request anything still completes"
    (let [future (ac/future)
          s (sub/subscription
             (config {})
             (reify Flow$Subscriber
               (onSubscribe [_ _])
               (onNext [_ _])
               (onError [_ _])
               (onComplete [_] (ac/complete! future true))))]
      (sub/close! s 0)

      (is (true? (deref future 10000 ::timeout)))))

  (testing "closing exceptionally signals onError"
    (let [future (ac/future)
          s (sub/subscription (config {}) (collecting-subscriber (atom []) future))]
      (sub/close-exceptionally! s (ba/ex-anom (ba/fault "msg-104953")))

      (given-failed-future future
        ::anom/category := ::anom/fault
        ::anom/message := "msg-104953")))

  (testing "closing twice signals onComplete only once"
    (let [completes (atom 0)
          s (sub/subscription
             (config {})
             (reify Flow$Subscriber
               (onSubscribe [_ s] (flow/request! s 1))
               (onNext [_ _])
               (onError [_ _])
               (onComplete [_] (swap! completes inc))))]
      (sub/close! s 0)
      (sub/close! s 0)

      ;; the thread of the subscription signals onComplete before it exits, so
      ;; waiting for it to exit is what makes a second signal observable
      (is (true? (sub/wait-until-finished! s)))
      (is (= 1 @completes))))

  (testing "the queued and the unqueued transactions of a subscriber that doesn't consume are dropped"
    ;; closing the node never waits for a subscriber that doesn't consume, so
    ;; the transactions its queue had no space for are lost as well
    (with-global-log-capture [dropped "Dropped the changed Task resources of 1 transaction(s) for the subscriber test because the node was closed before it consumed them."]
      (with-global-log-capture [unexamined "Never examined the transactions after t = 1 up to t = 3 for the changed Task resources subscriber test because the node was closed before they could be published to it."]
        (let [s (sub/subscription (config {:queue-capacity 1})
                                  (non-requesting-subscriber))]
          (testing "only the first of three transactions fits into the window"
            (sub/publish! s 1 (changed-handles (handles 1 "0")))
            (is (= 1 (sub/queued-t s))))

          (sub/close! s 3)

          (testing "the queued transaction is logged as dropped"
            (given (deref dropped 10000 ::timeout)
              :level := :warn))

          (testing "the transactions its queue had no space for are logged as never examined"
            ;; how many of them changed resources of the type isn't known,
            ;; because determining the changed handles is exactly what never
            ;; happened for them. So they are logged as the range they span
            ;; instead of as a count of transactions lost
            (given (deref unexamined 10000 ::timeout)
              :level := :warn))))))

  (testing "a subscriber that consumed everything queued still misses the transactions after its queued-t"
    (with-global-log-capture [dropped "Dropped the changed Task resources of 0 transaction(s) for the subscriber test because the node was closed before it consumed them."]
      (with-global-log-capture [unexamined "Never examined the transactions after t = 1 up to t = 3 for the changed Task resources subscriber test because the node was closed before they could be published to it."]
        (let [items (atom [])
              s (sub/subscription (config {:queue-capacity 1})
                                  (collecting-subscriber items (ac/future)))]
          (sub/publish! s 1 (changed-handles (handles 1 "0")))
          (is (true? (wait-for items #(= 1 (count %)))))

          (sub/close! s 3)

          (testing "nothing is logged as dropped, because its queue is empty"
            (is (nil? (deref dropped 100 nil))))

          (given (deref unexamined 10000 ::timeout)
            :level := :warn)))))

  (testing "closing a subscription that delivered all transactions drops nothing"
    (with-global-log-capture [captured "Dropped the changed Task resources of 0 transaction(s) for the subscriber test because the node was closed before it consumed them."]
      (let [items (atom [])
            future (ac/future)
            s (sub/subscription (config {}) (collecting-subscriber items future))]
        (sub/publish! s 1 (changed-handles (handles 1 "0")))
        (is (true? (wait-for items #(= 1 (count %)))))

        (sub/close! s 1)

        (is (true? (deref future 10000 ::timeout)))

        (testing "nothing is logged"
          (is (nil? (deref captured 100 nil))))))))

(deftest failing-subscriber-test
  (testing "a subscriber throwing in onNext is removed and receives the error"
    (with-global-log-capture [captured "Remove the changed Task resources subscriber test because of: msg-121500"]
      (let [removed (atom [])
            future (ac/future)
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (flow/request! s 1))
                 (onNext [_ _] (throw (Exception. "msg-121500")))
                 (onError [_ e] (ac/complete-exceptionally! future e))
                 (onComplete [_])))]
        (sub/publish! s 1 (changed-handles (handles 1 "0")))

        (given-failed-future future
          ::anom/message := "msg-121500")

        (is (true? (wait-for removed #(= [s] %))))

        (given (deref captured 10000 ::timeout)
          :level := :warn))))

  (testing "a subscriber cancelling in onError after throwing in onNext doesn't remove the subscription again"
    (with-global-log-capture [captured "Remove the changed Task resources subscriber test because it cancelled its subscription."]
      (let [removed (atom [])
            cancelled (promise)
            subscription (volatile! nil)
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (vreset! subscription s) (flow/request! s 1))
                 (onNext [_ _] (throw (Exception. "msg-155525")))
                 (onError [_ _]
                   (flow/cancel! @subscription)
                   (deliver cancelled true))
                 (onComplete [_])))]
        (sub/publish! s 1 (changed-handles (handles 1 "0")))

        (is (true? (deref cancelled 10000 nil)))
        (is (= [s] @removed))

        (testing "the cancelling isn't reported as the cause of the removal"
          (is (nil? (deref captured 100 nil)))))))

  (testing "a subscriber throwing in onComplete is removed"
    (with-global-log-capture [captured "Remove the changed Task resources subscriber test because of: msg-134606"]
      (let [removed (atom [])
            signalled (promise)
            s (sub/subscription
               (config {:removed removed})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (flow/request! s 1))
                 (onNext [_ _])
                 (onError [_ _] (deliver signalled true))
                 (onComplete [_] (throw (Exception. "msg-134606")))))]
        (sub/close! s 0)

        (testing "the subscription is removed instead of killing its thread"
          (is (true? (wait-for removed #(= [s] %)))))

        (given (deref captured 10000 ::timeout)
          :level := :error)

        (testing "the subscriber doesn't receive a second signal"
          ;; the reactive streams spec considers the subscription of a
          ;; subscriber that throws in its terminal signal as cancelled
          (sub/wait-until-finished! s)
          (is (nil? (deref signalled 0 nil)))))))

  (testing "a subscriber cancelling in onNext before throwing doesn't receive the error"
    (with-global-log-capture [captured "Remove the changed Task resources subscriber test because of: msg-103712"]
      (let [signalled (promise)
            subscription (volatile! nil)
            s (sub/subscription
               (config {})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (vreset! subscription s) (flow/request! s 1))
                 (onNext [_ _]
                   (flow/cancel! @subscription)
                   (throw (Exception. "msg-103712")))
                 (onError [_ _] (deliver signalled true))
                 (onComplete [_] (deliver signalled true))))]
        (sub/publish! s 1 (changed-handles (handles 1 "0")))

        (testing "the subscription is ended with that error anyway"
          (given (deref captured 10000 ::timeout)
            :level := :warn))

        (sub/wait-until-finished! s)

        (is (nil? (deref signalled 0 nil))))))

  (testing "a subscriber throwing in onError after throwing in onNext is logged"
    (with-global-log-capture [captured "Remove the changed Task resources subscriber test because of: msg-114530"]
      (let [s (sub/subscription
               (config {})
               (reify Flow$Subscriber
                 (onSubscribe [_ s] (flow/request! s 1))
                 (onNext [_ _] (throw (Exception. "msg-111213")))
                 (onError [_ _] (throw (Exception. "msg-114530")))
                 (onComplete [_])))]
        (sub/publish! s 1 (changed-handles (handles 1 "0")))

        (testing "instead of bypassing the logging as an uncaught exception"
          (given (deref captured 10000 ::timeout)
            :level := :error))

        (testing "and the thread of the subscription exits normally"
          (is (true? (sub/wait-until-finished! s))))))))

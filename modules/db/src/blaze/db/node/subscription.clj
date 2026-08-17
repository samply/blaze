(ns blaze.db.node.subscription
  "This namespace provides the subscription of a single subscriber to the
  resource handles changed on a database node.

  A subscription owns a bounded queue of resource handle vectors, one vector
  per transaction, and a thread that drains that queue into the onNext method
  of its subscriber.

  The publishing loop of the node fills that queue without ever blocking. So a
  subscriber that doesn't consume fast enough only fills up its own queue,
  while the other subscribers still receive their resource handles. A blocking
  onNext only blocks the thread of its own subscription.

  The subscription tracks the queued-t, the point in time up to which all
  transactions were examined for the subscriber. The publishing loop uses it to
  determine the transactions still to publish, bounded to the free slots of the
  queue, and skips a subscription with a full queue, retrying in a later round.
  Because the thread of a subscription wakes up the publishing loop after it
  freed half the capacity of its queue, such a later round doesn't depend on the
  next transaction to happen.

  Ending a subscription, either by the node closing it or by its subscriber
  violating the reactive streams spec, doesn't wait for its subscriber to
  consume. It drops the queue instead, so that nothing is delivered after the
  end, and closing logs the number of transactions lost that way as a warning,
  together with the range of transactions it never examined for the subscriber.
  Only a delivery that is already in progress still finishes.

  Creating a subscription runs the thread of its drain-loop and signals
  onSubscribe to its subscriber, so that a subscription is able to deliver from
  the moment it exists. It has nothing to deliver until the node registers it,
  because a subscription the node doesn't know about isn't published to. So the
  node never has a subscription in a startup state of its own to skip. The
  subscription completes its finished future after that thread exited, so that
  the node can wait for its last delivery with `wait-until-finished!`.

  The whole state of a subscription is held in a single atom, so that every
  decision is made on one consistent snapshot. The thread of the drain-loop
  waits for work by joining the signal future of the snapshot it decided to
  wait on. Because every state change replaces that signal with a fresh one and
  completes the old one, a wake-up can't be lost and no thread ever has to be
  interrupted."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [blaze.db.node.util :as node-util]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Flow$Subscription]))

(set! *warn-on-reflection* true)

(defn- slow-subscriber-msg [type name]
  (format "The changed %s resources subscriber %s doesn't consume fast enough. Transactions are still indexed, but its publishing lag will grow."
          type name))

(defn- cancelled-subscriber-msg [type name]
  (format "Remove the changed %s resources subscriber %s because it cancelled its subscription."
          type name))

(defn- failing-subscriber-msg [type name e]
  (format "Remove the changed %s resources subscriber %s because of: %s"
          type name (ex-message e)))

(defn- dropped-transactions-msg [type name n]
  (format "Dropped the changed %s resources of %d transaction(s) for the subscriber %s because the node was closed before it consumed them."
          type n name))

(defn- unexamined-transactions-msg [type name queued-t t]
  (format "Never examined the transactions after t = %d up to t = %d for the changed %s resources subscriber %s because the node was closed before they could be published to it."
          queued-t t type name))

(defn- non-positive-request-msg [n]
  (format "Non-positive request of %d items." n))

(defn- initial-state
  "Returns the initial state of a subscription starting at `queued-t`.

  The state contains the `:queue` of resource handle vectors, the `:queued-t`,
  the `:demand` of the subscriber, the `:end` of the subscription, which is
  either nil, ::complete or the error to signal, whether the subscriber
  `:cancelled?` its subscription, whether the subscription is `:lagging?` and
  the `:signal` the thread of the drain-loop waits on.

  A subscription is lagging from the moment the publishing loop finds its queue
  full while transactions are left to publish until its subscriber consumed
  everything queued. That span is one lag phase, and the slow subscriber is
  logged once at its start."
  [queued-t]
  {:queue clojure.lang.PersistentQueue/EMPTY
   :queued-t queued-t
   :demand 0
   :end nil
   :cancelled? false
   :lagging? false
   :signal (ac/future)})

(defn- swap-state!
  "Applies `f` to the state of `subscription` and wakes up the thread of its
  drain-loop, so that it examines the new state.

  Waking up is done by replacing the signal with a fresh one and completing the
  old one. Because the thread only ever waits on the signal it read together
  with the rest of the state, a wake-up can't be lost.

  Returns a vector of the old and the new state."
  {:arglists '([subscription f])}
  [{:keys [state]} f]
  (let [signal (ac/future)
        [old-state :as states] (swap-vals! state #(assoc (f %) :signal signal))]
    (ac/complete! (:signal old-state) true)
    states))

(defn- free-slots
  "Returns the number of resource handle vectors `queue` has space for."
  ^long [queue ^long queue-capacity]
  (- queue-capacity (count queue)))

(defn- queue-changed-handles
  "Returns `state` with the resource handle vectors of `changed-handles` that
  are after its queued-t up to and including `t` queued and its queued-t
  advanced to `t`.

  Skips the vectors after `t`, because the changed handles of a type are
  determined once for all its subscriptions, so they reach beyond the window of
  a subscription that has space for fewer transactions than another one.

  Doesn't bound the vectors queued by the capacity of the queue, because the
  publishing loop sizes the window of a round by the free slots of the
  subscription, so that everything it publishes fits. Bounding them here again
  would only hide a window that was sized wrong.

  Queues the whole round in one go, so that neither the thread of the
  subscription nor the publishing lag metric can observe it half done."
  [state t changed-handles]
  (let [t (long t)]
    (loop [{:keys [queued-t] :as state} state
           [handles & more] (rseq changed-handles)]
      (cond
        (or (nil? handles) (< t (long (:t (first handles)))))
        (assoc state :queued-t t)

        (<= (long (:t (first handles))) (long queued-t))
        (recur state more)

        :else
        (recur (update state :queue conj handles) more)))))

(defn- take-handles
  "Returns `state` with the first resource handle vector removed from its queue
  and its demand decremented, if the subscriber demands one and the queue isn't
  empty. Returns `state` unchanged otherwise.

  Ends the lag phase if the removal empties the queue, because a subscriber that
  consumed everything queued caught up. Ending it only then is what keeps a
  subscriber that stays slow in a single lag phase. Ending it with every removal
  instead would log that subscriber once per refill, because the publishing loop
  fills the freed space up again, finding the queue full afterwards."
  [{:keys [queue demand] :as state}]
  (let [demand (long demand)]
    (if (and (pos? demand) (seq queue))
      (let [queue (pop queue)]
        (cond-> (assoc state :queue queue :demand (dec demand))
          (empty? queue)
          (assoc :lagging? false)))
      state)))

(defn- taken-handles
  "Returns the resource handle vector `take-handles` removed while going from
  `old-state` to `new-state`, or nil if it removed none.

  Because the removal happened in a single compare-and-set, that vector was
  removed by exactly this state transition. Together with the subscription
  having only one thread, that's what makes every vector delivered exactly
  once."
  [old-state new-state]
  (when-not (identical? (:queue old-state) (:queue new-state))
    (peek (:queue old-state))))

(defn- low-water-mark
  "Returns the number of resource handle vectors left in a queue of
  `queue-capacity` at which the publishing loop is woken up.

  Half the capacity, so that the publishing loop refills half a queue at a time
  instead of once per delivered transaction. Waking it up at the first free slot
  would degenerate a subscription that lags behind into one round per delivery,
  each with its own batch database, while the space freed until it runs is a
  single transaction."
  ^long [^long queue-capacity]
  (quot queue-capacity 2))

(defn- end!
  "Ends `subscription` with `end` and drops its queue, unless it already ended,
  so that its subscriber receives onComplete or onError promptly, without the
  resource handle vectors still queued.

  Dropping the queue is what makes the node stop delivering as soon as it is
  closed. Delivering the queued resource handle vectors instead would run
  subscriber code after the node was closed, which is the moment its indexing
  loop stopped and the components it depends on start to shut down. A subscriber
  querying the node would race that shutdown and a subscriber transacting on it
  would submit a transaction no indexing loop will ever pick up.

  It's also what keeps a subscriber that violated the reactive streams spec from
  receiving onNext after that violation, which the spec expects to be signalled
  promptly."
  [subscription end]
  (swap-state! subscription
               (fn [state]
                 (cond-> state
                   (nil? (:end state))
                   (assoc :end end :queue clojure.lang.PersistentQueue/EMPTY)))))

(defn- fail!
  "Ends `subscription` with the error `e` and removes it from the node, unless
  it already ended, so that its subscriber receives onError and nothing is
  published to it anymore.

  Unlike closing, which the node does while removing all its subscriptions, a
  subscription that ends because its subscriber violated the reactive streams
  spec has to remove itself. Removing it here is also the only chance to do so,
  because the subscriber cancelling later doesn't remove an already ended
  subscription anymore."
  {:arglists '([subscription e])}
  [{:keys [type name remove!] :as subscription} e]
  (let [[old-state] (end! subscription e)]
    (when (nil? (:end old-state))
      (log/warn (failing-subscriber-msg type name e))
      (remove! subscription))))

(defn- end-subscriber!
  "Signals `end`, the end of `subscription`, to its subscriber, removing the
  subscription from the node if its onComplete or onError method throws.

  Doesn't signal anything after such a throw, because the reactive streams spec
  considers the subscription of a subscriber that throws in a terminal signal as
  cancelled. Removing it is all that is left to do, because the thread of the
  drain-loop exits right afterwards."
  {:arglists '([subscription end])}
  [{:keys [type name subscriber remove!] :as subscription} end]
  (try
    (if (identical? ::complete end)
      (flow/on-complete! subscriber)
      (flow/on-error! subscriber end))
    (catch Throwable e
      (log/error (failing-subscriber-msg type name e))
      (log/error e)
      (remove! subscription))))

(defn- finish!
  "Completes the finished future of `subscription`, so that the node stops
  waiting for it.

  Completed by the thread of the drain-loop after it exited."
  {:arglists '([subscription])}
  [{:keys [finished]}]
  (ac/complete! finished true))

(defn- drain-loop
  "Delivers the queued resource handle vectors of `subscription` to its
  subscriber, as demanded, until the subscription is cancelled or closed.

  Waits on the signal of the state for the subscriber to demand more or the
  publishing loop to queue more. Doesn't wait after the subscription ended, so
  that a subscriber that doesn't request anything anymore neither delays the end
  of the subscription nor keeps this thread alive. Ending empties the queue, so
  this loop ends without delivering anything else, at most finishing the
  delivery it is in.

  Signals onComplete or onError at the end, unless the subscriber cancelled its
  subscription, because a cancelled subscriber doesn't receive any signal
  anymore.

  Takes the next resource handle vector and examines the state in the same
  compare-and-set, so that what is delivered is always what this thread just
  removed from the queue.

  Ends `subscription` with the error, removes it from the node and signals that
  error to its subscriber if anything throws, because a subscriber that can't
  process the resource handles of one transaction can't process the following
  ones either and would wait for a signal that never comes otherwise. Ending it
  instead of only removing it is also what keeps a subscriber that cancels its
  subscription in onError from being reported as having dropped one or from
  removing it a second time. Doesn't signal a subscriber that cancelled its
  subscription meanwhile, just like the ordinary end isn't signalled to it.

  An error of a single subscription concerns neither the node nor its other
  subscribers. So, unlike the publishing loop, it neither stops the node nor
  rethrows, because this loop runs on a plain thread whose uncaught exceptions
  would bypass the logging.

  Finishes `subscription` as its very last action, so that the node waiting for
  it doesn't return while any subscriber code still runs."
  {:arglists '([subscription])}
  [{:keys [type name queue-capacity subscriber state wake-node-publish-loop!]
    :as subscription}]
  (log/trace "enter changed" type "resources subscriber" name)
  (try
    (loop []
      (let [[old-state new-state] (swap-vals! state take-handles)
            {:keys [end cancelled? signal]} old-state
            handles (taken-handles old-state new-state)]
        (cond
          cancelled?
          nil

          handles
          (do
            ;; the queue has space for half its capacity again, so the
            ;; publishing loop can queue the transactions it left out, even if
            ;; no new one is committed. Waking it up at exactly that point is
            ;; sufficient, because the queue is only ever drained one vector at
            ;; a time, so it can't pass the low-water mark without hitting it
            (when (== (low-water-mark queue-capacity) (count (:queue new-state)))
              (wake-node-publish-loop!))
            (flow/on-next! subscriber handles)
            (recur))

          (some? end)
          (end-subscriber! subscription end)

          :else
          (do (ac/join signal) (recur)))))
    (catch Throwable e
      (log/error e)
      (fail! subscription e)
      (when-not (:cancelled? @state)
        (end-subscriber! subscription e)))
    (finally
      (log/trace "exit changed" type "resources subscriber" name)
      (finish! subscription))))

(defn- start-thread!
  [{:keys [thread-name] :as subscription}]
  (node-util/start-thread! #(drain-loop subscription) thread-name))

(defn- add-demand
  "Returns `demand` increased by `n`, saturating at Long/MAX_VALUE.

  Because both are non-negative, the sum can only overflow into the negative
  range. Saturating instead of overflowing is required, because a demand of
  Long/MAX_VALUE means unbounded, so that adding more to it can't change
  anything, and because the request method isn't allowed to throw."
  [demand n]
  (let [sum (unchecked-add (long demand) (long n))]
    (if (neg? sum) Long/MAX_VALUE sum)))

(defn- mark-cancelled!
  "Marks `subscription` as cancelled, so that its subscriber doesn't receive any
  signal anymore and the thread of its drain-loop exits.

  Returns a vector of the old and the new state."
  [subscription]
  (swap-state! subscription #(assoc % :cancelled? true)))

(defn- cancel!
  "Cancels `subscription`, so that its subscriber doesn't receive any signal
  anymore, and removes it from the node."
  {:arglists '([subscription])}
  [{:keys [type name remove!] :as subscription}]
  (let [[old-state] (mark-cancelled! subscription)]
    (when-not (:cancelled? old-state)
      ;; cancelling an already ended subscription is the ordinary cleanup of a
      ;; subscriber that received onComplete or onError. It's neither still
      ;; registered nor is anything dropped.
      (when (nil? (:end old-state))
        (log/warn (cancelled-subscriber-msg type name))
        (remove! subscription)))))

(defrecord Subscription [type name thread-name queue-capacity remove!
                         wake-node-publish-loop! subscriber state finished]
  Flow$Subscription
  (request [this n]
    (if (pos? n)
      (swap-state! this #(update % :demand add-demand n))
      (fail! this (ba/ex-anom (ba/incorrect (non-positive-request-msg n))))))

  (cancel [this]
    (cancel! this)))

(defn subscription?
  "Returns true if `x` is a subscription."
  [x]
  (instance? Subscription x))

(defn- subscribe! [subscription subscriber]
  (try
    (flow/on-subscribe! subscriber subscription)
    (catch Throwable e
      (mark-cancelled! subscription)
      (throw e))))

(defn subscription
  "Returns a started subscription of `subscriber` to the changed resources of
  the type of `config`.

  The config contains the `:type` and the `:name` of the subscriber, the
  `:thread-name` and the `:queue-capacity` of its queue, the `:queued-t` to
  start from, a `:remove!` function that removes the subscription from the node
  and a `:wake-node-publish-loop!` function that wakes up the publishing loop
  of the node.

  Started means that the thread of its drain-loop runs and that `subscriber`
  received onSubscribe, so that the node has nothing left to initialize on the
  subscription it registers. Both are done here instead of in a second step,
  because a subscription is of no use without them. It still doesn't deliver
  anything before the node registered it, because a subscription the node
  doesn't know about isn't published to.

  Cancels the subscription and rethrows if the onSubscribe method of
  `subscriber` throws. Cancelling lets the thread of the drain-loop exit without
  signalling anything to a subscriber whose subscription the reactive streams
  spec considers as cancelled. Rethrowing is the only way left to report that
  failure, because such a subscriber can't receive onError anymore, while
  returning normally would leave the component that subscribed running with a
  subscription that never delivers anything."
  [{:keys [type name thread-name queue-capacity queued-t remove!
           wake-node-publish-loop!]}
   subscriber]
  (doto (->Subscription type name thread-name queue-capacity
                        remove! wake-node-publish-loop!
                        subscriber
                        (atom (initial-state queued-t))
                        (ac/future))
    (start-thread!)
    (subscribe! subscriber)))

(defn queued-t
  "Returns the point in time up to which all transactions were examined for the
  subscriber of `subscription`.

  Transactions that didn't change resources of the type of `subscription` are
  examined as well, so the queued-t also advances over them."
  {:arglists '([subscription])}
  [{:keys [state]}]
  (:queued-t @state))

(defn queued
  "Returns the number of transactions whose changed resources were queued for
  but not yet delivered to the subscriber of `subscription`.

  That's exactly the number of resource handle vectors in its queue, because the
  publishing loop queues one vector per transaction that changed resources of
  the type of `subscription`. Unlike `unexamined`, this counts no transaction
  that has nothing to deliver.

  Bounded by the queue capacity, so it saturates while a subscriber that doesn't
  consume falls further behind. `unexamined` is what shows how far behind it is."
  {:arglists '([subscription])}
  [{:keys [state]}]
  (count (:queue @state)))

(defn unexamined
  "Returns the number of t's after the queued-t of `subscription` up to and
  including `t`, the distance its examination is behind `t`.

  That distance is an upper bound on the transactions the subscriber still
  receives resource handles for, not a count of them. It spans the t's of the
  failed transactions, which change no resources at all, and of the successful
  ones that changed no resource of the type of `subscription`. Which of them are
  which is exactly what determining the changed handles finds out, so it can't
  be known for a transaction the publishing loop didn't examine yet.

  Unlike `window-t`, this can't decide on a single snapshot, because `t` comes
  from the state of the node while the queued-t comes from the state of
  `subscription`. So the subscription can have already examined transactions
  after `t` by the time its state is read. Returns zero in that case, so that
  the distance is never negative."
  {:arglists '([subscription t])}
  [{:keys [state]} t]
  (max 0 (- (long t) (long (:queued-t @state)))))

(defn- start-lag-phase!
  "Starts a lag phase of `subscription` and logs its slow subscriber, so that it
  is logged once at the start of a phase instead of once per round. Only its
  subscriber consuming everything queued ends that phase, by clearing the
  lagging state again.

  Has to be called with a subscription that isn't lagging already. Deciding that
  is left to the caller, because the publishing loop is the only thread that
  starts a lag phase and it holds the state snapshot it decided on anyway.

  Doesn't wake up the thread of the drain-loop, because a subscription that lags
  behind has a full queue, so there is nothing new for it to deliver."
  {:arglists '([subscription])}
  [{:keys [type name state]}]
  (swap! state assoc :lagging? true)
  (log/warn (slow-subscriber-msg type name)))

(defn window-t
  "Returns the point in time up to which the transactions after the queued-t of
  `subscription` are examined for it in one round, or nil if there is no round
  to run for it.

  Bounds the window to the free slots of its queue, because the resource handle
  vectors of more transactions can't be queued in this round anyway. Determining
  them for the whole capacity instead would make the publishing loop examine the
  transactions a subscriber that lags behind can't take again and again, once
  per transaction it consumed. The window can still cover more transactions than
  that, because a transaction that changed no resource of the type of
  `subscription` occupies no slot, and the publishing loop simply runs another
  round for the transactions left.

  Returns nil if `subscription` examined all transactions up to and including
  `t` already or if its queue is full. Starts a lag phase in the latter case,
  because a subscription with transactions left to examine and no space for them
  is exactly one whose subscriber doesn't consume fast enough. Doesn't touch the
  state of a subscription that is lagging already, so that a subscriber that
  stays slow doesn't make every round write its state.

  Decides on a single state snapshot, so that the space and the queued-t can't
  come from two different points in time."
  {:arglists '([subscription t])}
  [{:keys [queue-capacity state] :as subscription} t]
  (let [{:keys [queue queued-t lagging?]} @state
        t (long t)
        queued-t (long queued-t)]
    (when (< queued-t t)
      (let [free (free-slots queue queue-capacity)]
        (if (pos? free)
          (min t (+ queued-t free))
          (do (when-not lagging? (start-lag-phase! subscription)) nil))))))

(defn publish!
  "Queues the resource handle vectors of `changed-handles` that are after the
  queued-t of `subscription` up to and including `t`, in ascending transaction
  order, and advances its queued-t to `t`.

  The `t` is the `window-t` of the round, so that everything queued fits into
  the queue. The `changed-handles` are in descending transaction order, reach
  beyond `t` if another subscription of the same type has a wider window and are
  empty if none of the transactions changed resources of the type of
  `subscription`.

  Never blocks and queues the whole round in a single state change."
  {:arglists '([subscription t changed-handles])}
  [{:keys [type name] :as subscription} t changed-handles]
  (let [[old-state new-state]
        (swap-state! subscription #(queue-changed-handles % t changed-handles))
        queued (- (count (:queue new-state)) (count (:queue old-state)))]
    (when (pos? queued)
      (log/trace "Queued" queued "changed" type "resource handle vector(s) up to t ="
                 (:queued-t new-state) "for" name))
    nil))

(defn ended?
  "Returns true if `subscription` ended already, either because its subscriber
  cancelled it or because it was ended with onComplete or onError.

  The node uses it to remove a subscription that ended while it was created,
  because such an end tried to remove a subscription that wasn't registered yet
  and so removed nothing."
  {:arglists '([subscription])}
  [{:keys [state]}]
  (let [{:keys [cancelled? end]} @state]
    (or cancelled? (some? end))))

(defn wait-until-finished!
  "Waits until the thread of `subscription` exited, so that no code of its
  subscriber runs anymore.

  Waits at most for the delivery that thread is in, because closing a
  subscription drops its queue, so that its thread signals onComplete or
  onError and exits right afterwards."
  {:arglists '([subscription])}
  [{:keys [finished]}]
  @finished)

(defn close!
  "Closes `subscription` at `t`, the point in time the node reached, so that its
  subscriber receives onComplete without further resource handle vectors.

  Logs what `subscription` doesn't deliver anymore, because closing the node
  never waits for a subscriber that doesn't consume. That's the queued
  transactions and the ones after its queued-t, logged separately because only
  the queued ones are a known number of transactions. The ones after its
  queued-t were never examined for it, ordinarily because its queue was full, so
  how many of them changed resources of the type isn't known and can't be found
  out anymore. They are logged as the range of t's they span instead, which is
  also what tells the point in time the subscriber is up to date to.

  A delivery that is already in progress isn't interrupted, because the resource
  handle vector it delivers left the queue already."
  {:arglists '([subscription t])}
  [{:keys [type name] :as subscription} t]
  (let [[{:keys [queue queued-t]}] (end! subscription ::complete)
        dropped (count queue)]
    (when (pos? dropped)
      (log/warn (dropped-transactions-msg type name dropped)))
    (when (< (long queued-t) (long t))
      (log/warn (unexamined-transactions-msg type name queued-t t)))))

(defn close-exceptionally!
  "Closes `subscription`, so that its subscriber receives onError with `e`
  without further resource handle vectors.

  Drops the queued ones like `close!` does, because the node is going down
  either way. Doesn't log them, because its subscriber learns about the gap from
  onError."
  [subscription e]
  (end! subscription e))

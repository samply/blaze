(ns blaze.db.node
  "This namespace contains the local database node component.

  A node runs two threads, plus one per changed resources subscriber. The
  indexer thread polls the transaction log and indexes the resulting
  transaction data. The changed resources publisher thread queues the resource
  handles changed in each transaction into the subscriptions registered by
  `blaze.db.api/subscribe-changes!`. The thread of each subscription delivers
  the queued handles to its subscriber.

  All threads run independently of each other, so publishing can lag behind
  indexing. Transactions are visible in database values as soon as they are
  indexed, independent of whether they were already published. That lag is
  exposed per resource type and subscriber as two metrics, both of which grow if
  publishing is slower than indexing, either because determining the changed
  handles takes long or because a subscriber doesn't consume fast enough.
  `blaze_db_node_publishing_lag_transactions` is the number of transactions
  whose changed handles were queued but not delivered yet, bounded by the queue
  capacity. `blaze_db_node_publishing_lag_t` is the distance in t between the
  last transaction the node indexed and the point in time up to which
  transactions were examined for a subscriber, which is unbounded and so shows
  how far behind a subscriber is. That distance is only an upper bound on the
  transactions still to deliver, because it spans the failed transactions and
  the ones that changed no resource of the type of the subscriber, and how many
  of them there are is exactly what determining the changed handles finds out.

  As long as the node runs, publishing is lossless, so the lag is never reduced
  by skipping transactions. Within a subscription, every transaction is
  delivered exactly once and in transaction order. Neither indexing nor the
  other subscribers are ever slowed down by a subscriber that doesn't consume
  fast enough.

  Submitting a transaction starts by taking one of the places for in-flight
  transactions. If none is free, the submit returns a busy anomaly without
  writing anything. The node is the only place a transaction is turned away,
  because the transaction log accepts whatever is submitted to it. So after the
  resource contents were stored and the commands were submitted to the log, the
  transaction is on its way to being indexed. Once the log has assigned a `t`,
  the transaction moves from `:submitting` to `:in-flight`, where it stays until
  the indexer got to it. So `:submitting` and `:in-flight` together limit how far
  this node can run ahead of its own indexing, which in turn bounds the memory
  the not yet indexed transactions occupy.

  The t's in `:in-flight` are not the t's the `:waiters` wait for. `:in-flight`
  holds only the transactions this node submitted itself, so it bounds its own
  writes, while a waiter is registered for every t a caller waits for, which is
  a read syncing on a t just as much as a caller waiting for the result of a
  transaction. With distributed storage a read syncs on the last t of the
  transaction log, which can be a transaction another node submitted and so was
  never in-flight here. The two overlap, but neither contains the other and the
  number of waiters isn't bounded by the maximum number of in-flight
  transactions.

  Closing the node is the one point at which transactions are lost. It doesn't
  wait for a subscriber that doesn't consume. Instead it stops publishing and
  drops what a subscription didn't deliver yet, the queued transactions and the
  ones its full queue had no space for, both logged as a warning, before its
  subscriber receives onComplete. Nothing is delivered after the node was
  closed, because the delivery would run subscriber code against a node whose
  indexing loop stopped and whose dependencies are already shutting down. For
  the same reason, transactions submitted to a closed node are rejected and
  futures waiting for a t that will never be indexed complete exceptionally
  instead of waiting forever.

  Closing waits for the thread of every subscription still running, after the
  indexing and the publishing loop finished. Dropping the queues bounds that
  wait to the delivery a thread is in, while returning earlier would leave
  subscriber code running against the components the node depends on while they
  are being shut down."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.anom :as-alias db-anom]
   [blaze.db.api :as d]
   [blaze.db.impl.batch-db :as batch-db]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.db :as db]
   [blaze.db.impl.index :as index]
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.t-by-instant :as t-by-instant]
   [blaze.db.impl.index.tx-error :as tx-error]
   [blaze.db.impl.index.tx-success :as tx-success]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.query.compartment :as qc]
   [blaze.db.impl.query.system :as qs]
   [blaze.db.impl.query.type :as qt]
   [blaze.db.impl.thread :as thread]
   [blaze.db.kv :as kv]
   [blaze.db.node.protocols :as np]
   [blaze.db.node.resource-indexer :as resource-indexer]
   [blaze.db.node.resource-indexer.spec]
   [blaze.db.node.spec]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.subscription :as sub]
   [blaze.db.node.transaction :as tx]
   [blaze.db.node.tx-indexer :as tx-indexer]
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.verify :as verify]
   [blaze.db.node.util :as node-util]
   [blaze.db.node.validation :as validation]
   [blaze.db.node.version :as version]
   [blaze.db.node.waiters :as waiters]
   [blaze.db.resource-cache :as rc]
   [blaze.db.resource-cache.spec]
   [blaze.db.resource-store :as rs]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.tx-log :as tx-log]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.spec.references :as fsr]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.metrics.core :as metrics]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.scheduler :as sched]
   [blaze.spec]
   [blaze.util :refer [conj-vec str]]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [prometheus.alpha :as prom :refer [defcounter defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [clojure.lang PersistentQueue]
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn submit-tx [node tx-ops]
  (np/-submit-tx node tx-ops))

(defn tx-result
  "Waits for the transaction with `t` to happen on `node`.

  Returns a CompletableFuture that will complete with the database after the
  transaction in case of success or will complete exceptionally with an anomaly
  in case of a transaction error or other errors."
  [node t]
  (np/-tx-result node t))

(defhistogram duration-seconds
  "Node durations.

  The `node` label distinguishes the individual nodes like main and admin.

  The ops overlap, so their durations must not be summed to obtain the time of
  the indexing thread. The `index-resources` of a transaction runs concurrently
  with the `index-transactions` and `store-tx-entries` of that same transaction
  and with the ops of later transactions, because the indexing loop dispatches
  the resource indexing of later transactions before it applies earlier ones.

  The `index-resources` of a transaction is observed even if its
  `index-transactions` failed, because the resources were indexed and did cost
  time either way.

  The `await-resources` of one chunk is the time the indexing loop spends
  blocked until that chunk is indexed, which is the only op during which the
  loop does no work of its own. So the rate of its sum is the fraction of the
  time the loop waits for the resource indexing to catch up."
  {:namespace "blaze"
   :subsystem "db_node"}
  (take 16 (iterate #(* 2 %) 0.0001))
  "node" "op")

(defhistogram index-batch-transactions
  "Number of transactions the indexer indexes in one batch.

  The indexing loop polls the transaction log for up to 500 transactions at
  once and indexes them as one batch, overlapping the resource indexing across
  their boundaries. So the number of transactions per batch shows how much of
  that overlap the loop actually gets to use.

  Empty batches, which the loop polls whenever it has nothing to index, are not
  observed, because they say nothing about the size of a batch.

  The `node` label distinguishes the individual nodes like main and admin."
  {:namespace "blaze"
   :subsystem "db_node"}
  (take 11 (iterate #(* 2 %) 1))
  "node")

(defcounter submit-rejections-total
  "Number of transaction submits rejected because the maximum number of
  in-flight transactions was already reached.

  The `node` label distinguishes the individual nodes like main and admin."
  {:namespace "blaze"
   :subsystem "db_node"}
  "node")

(def ^:private ^:const default-max-in-flight-transactions 1024)

(defn- closed-node-msg [node-name]
  (format "The database node `%s` is closed." node-name))

(defn- closed-before-indexed-msg [node-name t]
  (format "The database node `%s` was closed before the transaction with t = %d was indexed. But it was stored durable and will be indexed at the next start of the database node."
          node-name t))

(defn- failed-node-msg [node-name]
  (format "The database node `%s` stopped because of an unrecoverable error." node-name))

(defn- failed-node-anom
  "Returns the anomaly every caller of the node with `node-name` receives after
  it failed.

  Reports that the node stopped instead of the error that stopped it, because
  that error can come from any depth of the node — an exception of the key-value
  store for example — and would otherwise be exposed to arbitrary clients,
  together with its stack trace. It's logged where it happens instead, which is
  also where its context is.

  Unavailable, like a closed node, because the node can't accept transactions
  anymore and only a restart brings it back."
  [node-name]
  (ba/unavailable (failed-node-msg node-name)))

(defn- reached?
  "Returns true if the transaction with `t` was already indexed in `state`,
  either successfully or with an error."
  [{current-t :t :keys [error-t]} t]
  (<= t (max current-t error-t)))

(defn- add-waiter
  "Returns `state` with a waiter for `t` registered, unless `t` was reached
  already, the node failed or the indexing loop finished.

  Registers nothing in those cases, because `t-future` completes right away
  then. A waiter registered would be left behind in the state, because no
  transaction will ever release it."
  [{:keys [failed? indexing-finished?] :as state} t]
  (cond-> state
    (not (or (reached? state t) failed? indexing-finished?))
    (update :waiters waiters/add t)))

(defn- reached-future
  "Returns a CompletableFuture completed with `f` applied to the t of the last
  successfully indexed transaction of `state`, or nil if `t` isn't reached in
  `state`.

  Applies `f` on the calling thread, because the caller doesn't have to wait."
  [state t f]
  (when (reached? state t)
    (ac/completed-future (f (:t state)))))

(defn- t-future
  "Returns a CompletableFuture that will complete with `f` applied to the t of
  the last successfully indexed transaction as soon as `t` is reached on `node`,
  or complete exceptionally in case the node failed or its indexing loop
  finished without reaching `t`.

  Examines the state without changing it first, because the t of a node only
  ever advances, so a state that shows `t` as reached can't become one that
  doesn't. That way a caller that doesn't have to wait doesn't write the state
  atom the indexing loop writes on every transaction. With distributed storage
  every request syncs on the last t of the transaction log, so that's the common
  case.

  Registers the waiter for `t` and decides on the state before that registration
  in a single swap otherwise, so `t` can't be reached in between, which would
  leave a waiter behind that no transaction releases anymore. Takes the waiter
  from the state that swap produced, because it's the waiter of the caller that
  registered one first that all callers waiting for `t` share.

  Applies `f` asynchronously in that case, because all callers waiting for the
  same `t` share a single future. A function applied synchronously would run on
  the single thread completing that future, one caller after the other, instead
  of on a thread per caller. For the same reason the shared future never leaves
  this function. Completing or cancelling it would affect all callers waiting
  for that `t`."
  [{:keys [node-name state]} t f]
  (or (reached-future @state t f)
      (let [[{:keys [failed? indexing-finished?] :as old-state} {new-waiters :waiters}]
            (swap-vals! state add-waiter t)]
        (or (reached-future old-state t f)
            (cond
              failed?
              (ac/completed-future (failed-node-anom node-name))

              ;; no transaction will ever be indexed again, so `t` will never be
              ;; reached
              indexing-finished?
              (ac/completed-future (ba/unavailable (closed-before-indexed-msg node-name t)))

              :else
              (ac/then-apply-async (new-waiters t) f))))))

(defn- acquire-in-flight
  "Counts one more transaction as submitting.

  Returns `state` unchanged if the maximum number of in-flight transactions is
  already reached."
  [{:keys [submitting in-flight] :as state} max-in-flight-transactions]
  (cond-> state
    (< (+ submitting (count in-flight)) max-in-flight-transactions)
    (update :submitting inc)))

(defn- max-in-flight-msg [max-in-flight-transactions]
  (format "The maximum number of %d in-flight transactions is reached. Please try again later."
          max-in-flight-transactions))

(defn- acquire-in-flight-fn
  "Returns a function that tries to take one of the places for in-flight
  transactions.

  That function returns a rejection anomaly and counts the rejection if the
  maximum number of in-flight transactions is already reached. It's the only
  point at which a transaction is turned away, so it has to be called before any
  data of the transaction is stored. That's also why the anomaly is categorized
  as a rejection, which tells a caller that submitting the transaction again is
  safe."
  [node-name state max-in-flight-transactions]
  (fn []
    (let [[old new] (swap-vals! state acquire-in-flight
                                max-in-flight-transactions)]
      (when (identical? old new)
        (prom/inc! submit-rejections-total node-name)
        (ba/busy (max-in-flight-msg max-in-flight-transactions)
                 ::db-anom/category :submit-rejected)))))

(defn- settle-in-flight
  "Stops counting the transaction as submitting and starts counting it as
  in-flight under its `new-t`.

  A transaction without a `new-t` never made it into the transaction log and one
  that is already indexed was released by `release-in-flight` before its `t`
  became known here. Both only stop being counted as submitting."
  [{:keys [t error-t] :as state} new-t]
  (cond-> (update state :submitting dec)
    (and new-t (< (max t error-t) new-t))
    (update :in-flight conj new-t)))

(defn- release-in-flight
  "Stops counting the transactions up to `t` as in-flight, because they are
  indexed now."
  [{:keys [in-flight] :as state} t]
  (assoc state :in-flight (reduce disj in-flight (subseq in-flight <= t))))

(defn- index-tx [node-name context tx-data]
  (with-open [_ (prom/timer duration-seconds node-name "index-transactions")]
    (ba/try-anomaly (ac/join (tx-indexer/index-tx context tx-data)))))

(defn- swap-state!
  "Applies `f` to `state` and wakes up the publishing loop, so that it examines
  the new state instead of waiting for the next transaction.

  Waking up is done by replacing the publish future with a fresh one and
  completing the old one. Because the publishing loop only ever waits on the
  publish future it read together with the rest of the state, a wake-up can't
  be lost.

  Returns the old state, so that the caller can complete the waiters `f`
  removed."
  [state f]
  (let [future (ac/future)
        [old-state] (swap-vals! state #(assoc (f %) :publish-future future))]
    (ac/complete! (:publish-future old-state) true)
    old-state))

(defn- wake-publish-loop!
  "Wakes up the publishing loop, so that it re-examines `state` instead of
  waiting for the next transaction."
  [state]
  (swap-state! state identity))

(defn- advance-t!
  "Advances `state` to `t`, stops counting the transactions up to `t` as
  in-flight and completes the waiters `t` released."
  [state t]
  (log/trace "advance state to t =" t)
  (let [{old-waiters :waiters}
        (swap-state! state #(-> (assoc % :t t)
                                (release-in-flight t)
                                (update :waiters waiters/remove-ready t)))]
    (waiters/complete-ready! old-waiters t t)))

(defn- advance-error-t!
  "Advances `state` to the error-t `t`, stops counting the transactions up to
  `t` as in-flight and completes the waiters `t` released.

  Completes them with the t of the last successful transaction, because a failed
  transaction produces no new database value.

  Doesn't wake up the publishing loop either, because a failed transaction
  changes no resources, so there is nothing to publish for it."
  [state t]
  (log/trace "advance state to error-t =" t)
  (let [[{old-waiters :waiters current-t :t}]
        (swap-vals! state #(-> (assoc % :error-t t)
                               (release-in-flight t)
                               (update :waiters waiters/remove-ready t)))]
    (waiters/complete-ready! old-waiters t current-t)))

(defn- commit-error! [{:keys [kv-store state]} t anomaly]
  (log/trace "commit transaction error with t =" t)
  (kv/put! kv-store [(tx-error/index-entry t anomaly)])
  (advance-error-t! state t))

(defn- store-tx-entries! [node-name kv-store entries]
  (log/trace "store" (count entries) "transaction index entries")
  (with-open [_ (prom/timer duration-seconds node-name "store-tx-entries")]
    (kv/put! kv-store entries)))

(defn- tx-success-entries [t instant]
  [(tx-success/index-entry t instant)
   (t-by-instant/index-entry instant t)])

(defn- changed-handles
  "Returns a vector of the resource handles of `type` changed in each
  transaction after `since-t` up to and including `window-t`, one vector per
  transaction, in descending transaction order.

  Starts the type history of `db` at `window-t` and stops it at `since-t`, so
  that only the transactions of that window are examined, independent of the
  wider window of `db`. That's what keeps a subscription that lags behind from
  widening the transactions examined for an unrelated type, while a single batch
  database is still sufficient for all of them.

  Relies on `d/type-history` returning the handles in descending transaction
  order, so the handles can be partitioned by t directly."
  [db type ^long since-t window-t]
  (into
   []
   (comp (take-while #(< since-t (long (:t %)))) (partition-by :t))
   (d/type-history db type window-t)))

(defn- remove-subscription* [subscriptions subscription]
  (into [] (remove #(identical? subscription %)) subscriptions))

(defn- remove-subscription [subscriptions type subscription]
  (let [remaining (remove-subscription* (subscriptions type) subscription)]
    (if (empty? remaining)
      (dissoc subscriptions type)
      (assoc subscriptions type remaining))))

(defn- remove-subscription!
  "Removes `subscription` from `state`, so nothing will be published to it
  anymore.

  Leaves it among the running subscriptions, because its thread can still be in
  a delivery. A subscriber cancels or fails at a point of its own choosing, so
  that has to stop the publishing right away, while the node still has to wait
  for that delivery when it closes."
  [state type subscription]
  (swap! state update :subscriptions remove-subscription type subscription))

(defn- min-since-t
  "Returns the smallest since-t of the ready subscriptions `ready`, the point in
  time after which the transactions have to be examined for at least one of
  them."
  [ready]
  (transduce (map :since-t) min Long/MAX_VALUE ready))

(defn- max-window-t
  "Returns the largest window-t of the ready subscriptions `ready`, the point in
  time up to which the transactions have to be examined for at least one of
  them."
  [ready]
  (transduce (map :window-t) max 0 ready))

(defn- publish-changed-resources!
  "Publishes the resource handles changed in the transactions of the window of
  each subscription of `ready-subscriptions`, a map of type to the ready
  subscriptions of that type.

  Determines the changed handles of all of them with a single batch database of
  the union of their windows, so that a round needs a single snapshot of the
  key-value store, independent of how far the individual subscriptions are
  apart. Examines only the union of the windows of the subscriptions of a type
  for that type, so that a subscription that lags behind doesn't cause the
  history of an unrelated type to be re-examined.

  Publishes the changed handles of a type to all its subscriptions, each one
  bounded to its own window. That window is the widest one such a subscription
  can take, so nothing is lost by that bound. The publishing loop runs another
  round for the transactions left.

  Transactions that didn't change resources of the type of a subscription are
  not published to it."
  [node ready-subscriptions]
  (let [all-ready (into [] cat (vals ready-subscriptions))
        window-t (max-window-t all-ready)]
    (with-open [db (batch-db/new-batch-db node window-t window-t
                                          (min-since-t all-ready))]
      (run!
       (fn [[type ready]]
         (let [handles (changed-handles db type (min-since-t ready)
                                        (max-window-t ready))]
           (run!
            (fn [{:keys [subscription window-t]}]
              (sub/publish! subscription window-t handles))
            ready)))
       ready-subscriptions))))

(defn- ready-subscription
  "Returns `subscription` together with the window of transactions to publish to
  it in one round, or nil if there is no round to run for it.

  Reads its queued-t after that window, because only the publishing loop
  advances it, so that both belong to the same round."
  [subscription t]
  (when-let [window-t (sub/window-t subscription t)]
    {:subscription subscription
     :since-t (sub/queued-t subscription)
     :window-t window-t}))

(defn- ready-subscriptions
  "Returns a map of type to the `subscriptions` of that type that are ready to
  receive the transactions up to and including `t`, so that a publishing round
  makes progress on each of them.

  Groups them by type, because the changed handles of a type are determined once
  per round for all its subscriptions. Subscriptions with a full queue are left
  out, so that a subscriber that doesn't consume fast enough neither holds the
  other subscriptions back nor makes the publishing loop determine changed
  handles it can't queue anyway."
  [subscriptions t]
  (into
   {}
   (keep
    (fn [[type subscriptions]]
      (when-let [ready (not-empty (into [] (keep #(ready-subscription % t)) subscriptions))]
        [type ready])))
   subscriptions))

(defn- close-subscription!
  "Closes `subscription` at `t`, the point in time the node reached, so that its
  subscriber receives onComplete, or onError if the node `failed?`.

  Signals the general anomaly of a failed node, not the error that stopped it.
  The subscribers are components of Blaze that only log what they receive, while
  the error itself is logged with its stack trace where it happens.

  Logs the transactions up to and including `t` that `subscription` didn't
  deliver yet, because closing drops them. Doesn't log them if the node failed,
  because its subscriber learns about the gap from onError."
  [node-name subscription t failed?]
  (if failed?
    (sub/close-exceptionally! subscription (ba/ex-anom (failed-node-anom node-name)))
    (sub/close! subscription t)))

(defn- close-subscriptions!
  "Removes all subscriptions from `state`, marks it as closed for further
  subscriptions and closes them, so that their subscribers receive onComplete,
  or onError if the node failed.

  Closes them at the t of `state`, so that the transactions a subscription
  didn't deliver yet are logged as dropped. That's the t of the last successful
  transaction, not the error-t of a later failed one, because a failed
  transaction changes no resources. So there are no handles to drop for it and
  no subscription ever advances over it."
  [node-name state]
  (let [[{:keys [t failed? subscriptions]}]
        (swap-vals! state assoc :subscriptions {} :subscriptions-closed? true)]
    (run! #(close-subscription! node-name % t failed?) (mapcat val subscriptions))))

(defn- add-subscription
  "Returns `state` with `subscription` registered for publishing and added to
  its running subscriptions, unless its subscriptions are closed already.

  Both are done in the same state change, so that a subscription is either
  already among the running ones when `close-subscriptions!` runs or isn't
  started at all. Otherwise closing the node could miss the thread of a
  subscription registered concurrently."
  [state type subscription]
  (cond-> state
    (not (:subscriptions-closed? state))
    (-> (update-in [:subscriptions type] conj-vec subscription)
        (update :running-subscriptions conj subscription))))

(defn- remove-running-subscription-when-finished!
  "Removes `subscription` from the running subscriptions of `state` as soon as
  its thread exited, so that they don't accumulate over the lifetime of the
  node.

  Removes it independent of whether it is still registered for publishing,
  because a subscription that was removed because its subscriber cancelled or
  failed still runs subscriber code until its thread exited."
  [state subscription]
  (-> (:finished subscription)
      (ac/when-complete
       (fn [_ _] (swap! state update :running-subscriptions
                        remove-subscription* subscription)))))

(defn- wait-for-subscriptions!
  "Waits until the threads of all running subscriptions of `state` exited, so
  that no subscriber code runs anymore after the node was closed.

  Has to happen before closing the node returns, because a delivery is
  subscriber code running against the node. Returning while one is in progress
  would let Integrant shut down the components the node depends on, like its
  key-value store, under that subscriber.

  Still doesn't wait for a subscriber that doesn't consume, because closing a
  subscription drops its queue. So each thread only finishes the delivery it is
  in, signals onComplete or onError and exits.

  Waits without a timeout, like the rest of closing the node does. A timeout
  would return while subscriber code still runs, which is what this wait exists
  to prevent."
  [state]
  (run! sub/wait-until-finished! (:running-subscriptions @state)))

(defn- subscription-config
  "Returns the config of a subscription of the subscriber `name` to the changed
  resources of `type` on `node`.

  Starts at the current t of `node`, so that only transactions committed after
  that t are published to it."
  [{:keys [node-name state queue-capacity]} type name]
  {:type type
   :name name
   :thread-name (format "%s-changed-%s-resources-subscriber-%s" node-name type name)
   :queue-capacity queue-capacity
   :queued-t (:t @state)
   :remove! (partial remove-subscription! state type)
   :wake-node-publish-loop! #(wake-publish-loop! state)})

(defn- register-subscriber!
  "Creates a subscription of `subscriber` to the changed resources of `type` on
  `node` and registers it in the state of `node`, so that the publishing loop
  fills its queue.

  Creating it already signals onSubscribe to `subscriber` and runs the thread
  draining its queue. So the publishing loop never sees a subscription whose
  queue nothing drains or whose subscriber didn't even receive onSubscribe yet,
  which would be reported as slow although it had no chance to consume anything.

  Removes it again if it ended meanwhile, because a subscriber can cancel its
  subscription or violate the reactive streams spec at any time from any thread
  after it received onSubscribe. Both remove the subscription, which removes
  nothing as long as it isn't registered, so it would be published to forever
  otherwise.

  Closes it instead of registering it if the subscriptions of the state were
  already closed, because the publishing loop has exited already, so that
  `subscriber` receives onComplete or onError instead of waiting for handles
  that will never come.

  Registers nothing and throws if the onSubscribe method of `subscriber` throws,
  because creating the subscription rethrows that error. So the node is left
  without a subscription that never delivers anything and the caller of
  `blaze.db.api/subscribe-changes!` learns about the failure.

  Returns nil."
  [{:keys [node-name state] :as node} type name subscriber]
  (let [subscription (sub/subscription (subscription-config node type name)
                                       subscriber)
        [{:keys [t failed? subscriptions-closed?]}]
        (swap-vals! state add-subscription type subscription)]
    (if subscriptions-closed?
      (close-subscription! node-name subscription t failed?)
      (do (remove-running-subscription-when-finished! state subscription)
          (if (sub/ended? subscription)
            (remove-subscription! state type subscription)
            ;; the publishing loop possibly waits for the next transaction,
            ;; while the queue of the new subscription can be filled already
            (wake-publish-loop! state)))))
  nil)

(defn- stop!
  "Stops the indexing loop of `state`."
  [state]
  (swap-state! state #(assoc % :run? false)))

(defn- fail!
  "Records that the node with `node-name` failed in `state`, stops the indexing
  loop and fails all waiters, so that no further transactions are completed and
  the subscribers as well as the callers waiting for a transaction are informed.

  Records only that the node failed, not the error that failed it. Nothing needs
  that error afterwards: every caller receives the general anomaly of a failed
  node and the error itself is logged with its stack trace where it happens.

  Wakes up the publishing loop, so that it doesn't wait for a next transaction
  that will never come, independent of where the node was failed from."
  [node-name state]
  (-> (swap-state! state #(assoc % :failed? true :run? false
                                 :waiters waiters/empty-waiters))
      :waiters
      (waiters/fail-all! (constantly (ba/ex-anom (failed-node-anom node-name))))))

(defn- publish-loop
  "Queues the resource handles changed in each transaction into the
  subscriptions registered on `node`, until `node` is closed.

  Runs on its own thread because determining the changed handles is expensive.
  Doing it on the indexer thread would slow down transaction indexing.

  Never blocks. Queueing the handles of a subscription with a full queue is
  retried in a later round, triggered by the thread of that subscription after
  it freed half the capacity of its queue. So a single thread is sufficient,
  while a subscriber that doesn't consume fast enough neither delays the other
  subscribers nor the closing of `node`.

  Waits on the publish future of the state for new transactions to become
  available. Transactions committed while publishing is in progress are
  coalesced into the next round, so that the changed handles of multiple
  transactions are determined with a single batch database, independent of the
  transaction rate. Because this loop is the only thread queueing handles, each
  subscription still receives the transactions in order.

  Publishes at most as many transactions per subscription and round as its queue
  has space for, because more can't be queued anyway. A subscription that lags
  behind catches up over multiple rounds that way, instead of every round
  examining all transactions it lags behind.

  Publishing isn't optional. So determining the changed handles failing stops
  the node the same way an indexing error does, because continuing would leave
  the subscribers with a silent gap. For the same reason, a subscriber that
  doesn't consume fast enough isn't dropped. Its handles stay queued, which is
  exposed as publishing lag.

  Runs until the indexing loop has finished and all its transactions are queued
  into every subscription with space left. A subscription whose queue is still
  full at that point is left behind at its queued-t, because waiting for a
  subscriber that doesn't consume would keep the node from closing. Closing a
  subscription drops what it didn't deliver yet and logs how many transactions
  that were."
  [{:keys [node-name state index-finished publish-finished] :as node}]
  (log/trace "enter changed resources publisher")
  (try
    (loop []
      ;; the publish future has to be read before the indexing loop is checked
      ;; to be finished, so that a wakeup happening in between isn't lost. Such
      ;; a wakeup completes that publish future, because every wakeup completes
      ;; the publish future current at that time and only replaces it afterwards
      ;;
      ;; the check of the indexing loop in turn has to happen before the rest of
      ;; the state is read, so that the state contains all transactions of an
      ;; already finished indexing loop
      (let [publish-future (:publish-future @state)
            indexing-finished? (ac/done? index-finished)
            {:keys [t subscriptions]} @state]
        (if-let [subscriptions (not-empty (ready-subscriptions subscriptions t))]
          (do (publish-changed-resources! node subscriptions)
              (recur))

          (when-not indexing-finished?
            (ac/join publish-future)
            (recur)))))
    (catch Throwable e
      (fail! node-name state)
      (log/error "Error while publishing changed resources:" (ex-message e))
      (log/error e))
    (finally
      (try
        (close-subscriptions! node-name state)
        (finally
          (ac/complete! publish-finished true)
          (log/trace "exit changed resources publisher"))))))

(defn- commit-success! [{:keys [node-name kv-store state]} t instant]
  (log/trace "commit transaction success with t =" t)
  (with-open [_ (prom/timer duration-seconds node-name "store-tx-success-entries")]
    (kv/put! kv-store (tx-success-entries t instant)))
  (advance-t! state t))

(defn- observe-index-resources!
  "Observes `timer` after all chunks of a transaction completed, so that it
  measures when its resources were actually indexed and not when the loop got
  around to awaiting them.

  Only waits for the chunks in `pending`, because the ones a transaction
  dispatched before them were awaited already and so completed by definition.
  That keeps the wait bounded by the look-ahead instead of by the number of
  chunks of the transaction."
  [timer pending]
  (-> (ac/all-of (mapv second pending))
      (ac/when-complete (fn [_ _] (prom/observe-duration! timer)))))

(defn- verify-tx!
  "Verifies the transaction of `tx-chunk-state` against the database value
  before it and stores its transaction index entries — step 2 of applying a
  transaction.

  Doesn't wait for the resources of that transaction, because the entries it
  writes are keyed by its `t` and stay invisible until `commit-tx!` moves the
  head of the TxSuccess index. Only that last step needs the resources to be
  indexed.

  Returns `tx-chunk-state` marked as verified, carrying the anomaly of a
  rejected transaction, so that `commit-tx!` commits the error instead of the
  success.

  Drops the chunks of a rejected transaction that aren't dispatched yet, because
  a transaction that produces no version references none of the index entries
  they would write. The ones already dispatched are still awaited, so that no
  task outlives the loop."
  [{:keys [node-name kv-store read-only-matcher stats] :as node}
   {{:keys [t tx-cmds] :as tx-data} :tx-data
    :keys [chunks pending timer] :as tx-chunk-state}]
  (log/trace "index transaction with t =" t "and" (count tx-cmds) "command(s)")
  (let [result (index-tx node-name {:db-before (np/-db node)
                                    :read-only-matcher read-only-matcher
                                    :stats @stats} tx-data)]
    (if (ba/anomaly? result)
      (do
        ;; dropping the chunks left makes the ones dispatched the last ones, so
        ;; the timer is observed here instead of by `dispatch-chunk!`
        (when (seq chunks)
          (observe-index-resources! timer pending))
        (assoc tx-chunk-state :verified? true :anomaly result :chunks []))
      (do
        (store-tx-entries! node-name kv-store (:entries result))
        ;; taking the stats over only after the entries were stored keeps them
        ;; in sync with the indexes, so that a transaction that wasn't stored
        ;; leaves them untouched
        (vreset! stats (:stats result))
        (assoc tx-chunk-state :verified? true)))))

(defn- commit-tx!
  "Commits either the success or the error of the transaction of
  `tx-chunk-state` — step 3 of applying a transaction.

  Runs after the resources of that transaction were indexed, because the success
  marker is what makes them reachable."
  [node {{:keys [t instant]} :tx-data :keys [anomaly]}]
  (if anomaly
    (commit-error! node t anomaly)
    (commit-success! node t instant)))

(defn- cmd-rs-keys
  "Returns the resource store keys of all resources of `tx-cmds` that have to be
  indexed.

  Kept resources are excluded because they didn't change and so are already
  indexed. Commands like delete carry no :hash and are excluded that way."
  [tx-cmds variant]
  (into
   []
   (keep
    (fn [{:keys [op type hash]}]
      (when (and hash (not= "keep" op))
        [(keyword "fhir" type) hash variant])))
   tx-cmds))

(defn- hash-resource-pairs [resources]
  (mapv (fn [[[_type hash] resource]] [hash resource]) resources))

(defn- tx-chunks
  "Returns a tuple of the chunks of resources of `tx-data` that have to be
  indexed and a function fetching the resources of one chunk.

  Uses the local payload if this node submitted the transaction and fetches the
  resources from the resource store otherwise. Both cases cover the same set of
  resources: kept resources are excluded because they didn't change and so are
  already indexed, and commands like delete carry no hash and are excluded that
  way."
  [{:keys [resource-store]} chunk-size {:keys [tx-cmds] payload :local-payload}]
  (if payload
    [(mapv vec (partition-all chunk-size payload)) ac/completed-future]
    [(mapv vec (partition-all chunk-size (cmd-rs-keys tx-cmds :complete)))
     #(-> (rs/multi-get resource-store %) (ac/then-apply hash-resource-pairs))]))

(defn- init-tx-chunk-state
  "Returns the state the indexing loop keeps for `tx-data`: the chunks of
  resources left to dispatch, the chunks dispatched but not yet awaited together
  with their size and whether the transaction was verified already.

  Keeps the chunks dispatched but not yet awaited in a queue, because a vector
  sliced by `subvec` would keep the chunks already awaited alive until the
  transaction is applied."
  [node chunk-size {:keys [instant] :as tx-data}]
  (let [[chunks fetch] (tx-chunks node chunk-size tx-data)]
    {:tx-data tx-data
     :last-updated (node-util/instant instant)
     :fetch fetch
     :chunks chunks
     :pending PersistentQueue/EMPTY
     :verified? false}))

(defn- index-chunk!
  "Starts indexing one chunk of resources, returning a future that completes
  after all resources of that chunk are indexed."
  [resource-indexer last-updated fetch chunk]
  (-> (fetch chunk)
      (ac/then-compose
       (fn [entries]
         (ac/all-of
          (mapv (fn [[hash resource]]
                  (resource-indexer/index-resource resource-indexer last-updated
                                                   hash resource))
                entries))))))

(defn- dispatch-chunk!
  "Dispatches the next chunk of `tx-chunk-state`, returning `tx-chunk-state`
  with that chunk moved from the chunks left to dispatch to the chunks
  dispatched but not yet awaited.

  Starts the `index-resources` timer with the first chunk of a transaction and
  observes it after all its chunks completed, so that it measures when the
  resources of that transaction were actually indexed and not when the loop got
  around to awaiting them."
  [{:keys [node-name resource-indexer]}
   {:keys [last-updated fetch chunks pending timer] :as tx-chunk-state}]
  (let [[chunk & more] chunks
        timer (or timer (prom/timer duration-seconds node-name "index-resources"))
        future (index-chunk! resource-indexer last-updated fetch chunk)
        pending (conj pending [(count chunk) future])]
    (when-not more
      (observe-index-resources! timer pending))
    (assoc tx-chunk-state :chunks more :timer timer :pending pending)))

(defn- await-chunk!
  "Awaits the chunk of `tx-chunk-state` that was dispatched first and isn't
  awaited yet, returning a tuple of `tx-chunk-state` without that chunk and the
  number of resources it contained.

  Observes the time spent waiting as `await-resources`, because that is the time
  the indexing loop is blocked instead of doing work of its own. Observes it on
  an error as well, because the loop did wait either way."
  [node-name {:keys [pending] :as tx-chunk-state}]
  (let [[size future] (peek pending)]
    (with-open [_ (prom/timer duration-seconds node-name "await-resources")]
      (try
        (ac/join future)
        (catch Throwable e
          (log/error "Error while resource indexing: " (ex-message (ex-cause e)))
          (log/error e)
          (throw e))))
    [(assoc tx-chunk-state :pending (pop pending)) size]))

(defn- await-quietly! [future]
  (try
    (ac/join future)
    (catch Throwable _)))

(defn- drain!
  "Awaits all chunks of `tx-chunk-states` that were dispatched but not awaited
  yet, discarding their results and exceptions, so that no resource indexing
  task outlives the indexing loop.

  Bounded by the look-ahead, because that is what limits the resources
  dispatched but not yet awaited."
  [tx-chunk-states]
  (run! (fn [{:keys [pending]}]
          (run! (fn [[_ future]] (await-quietly! future)) pending))
        tx-chunk-states))

(defn- next-dispatch
  "Returns the index of the first transaction of `tx-chunk-states` at or after
  `idx` that still has a chunk left to dispatch."
  [tx-chunk-states idx]
  (let [n (count tx-chunk-states)]
    (loop [idx idx]
      (if (and (< idx n) (empty? (:chunks (nth tx-chunk-states idx))))
        (recur (inc idx))
        idx))))

(defn- index-batch!
  "Indexes the transactions of `batch` in order.

  Works with the `:index-bounds` of the node, keeping at most its look-ahead of
  resources dispatched but not yet awaited, across transaction boundaries and
  within a single transaction, so that the resource indexer executor doesn't
  idle between transactions while neither the number of concurrent fetches of
  the resource store nor the number of decoded resources held in memory scales
  with the size of a transaction. A chunk always fits into the look-ahead, so
  the loop can always dispatch.

  Verifies and stores the transaction index entries of the head transaction as
  soon as it is the head, without waiting for its own resources, so that a batch
  of a single transaction overlaps them as well. Only the commit of that
  transaction waits for them, and a rejected one doesn't dispatch the chunks it
  has left at all.

  Chunks are awaited in dispatch order and transactions are applied in order, so
  the order of the observable effects is unchanged. Stops after the transaction
  that turned `:run?` of `state` off, which makes closing responsive
  independently of the size of the batch. Whatever is left dispatched is awaited
  in the end, on a normal end, on a stop and on an exception alike."
  [{:keys [node-name state] {:keys [chunk-size look-ahead]} :index-bounds
    :as node} batch]
  (when (seq batch)
    (prom/observe! index-batch-transactions node-name (count batch)))
  (let [tx-chunk-states (volatile! (mapv (partial init-tx-chunk-state node
                                                  chunk-size)
                                         batch))
        n (count @tx-chunk-states)]
    (try
      (loop [head 0
             dispatch (next-dispatch @tx-chunk-states 0)
             in-flight 0]
        (when (< head n)
          (let [current @tx-chunk-states
                next-size (when (< dispatch n)
                            (count (first (:chunks (nth current dispatch)))))]
            (cond
              (and next-size (<= (+ in-flight next-size) look-ahead))
              (do (vswap! tx-chunk-states assoc dispatch
                          (dispatch-chunk! node (nth current dispatch)))
                  (recur head (next-dispatch @tx-chunk-states dispatch)
                         (long (+ in-flight next-size))))

              (not (:verified? (nth current head)))
              (do (vswap! tx-chunk-states assoc head
                          (verify-tx! node (nth current head)))
                  ;; a rejected transaction drops the chunks it has left, so the
                  ;; dispatch index can point at a transaction without chunks now
                  (recur head (next-dispatch @tx-chunk-states dispatch) in-flight))

              (seq (:pending (nth current head)))
              (let [[tx-chunk-state size] (await-chunk! node-name
                                                        (nth current head))]
                (vswap! tx-chunk-states assoc head tx-chunk-state)
                (recur head dispatch (long (- in-flight size))))

              :else
              (do (commit-tx! node (nth current head))
                  (when (:run? @state)
                    (recur (inc head) dispatch in-flight)))))))
      (finally
        (drain! @tx-chunk-states)))))

(defn- poll-tx-log! [node-name tx-log offset poll-timeout]
  (with-open [_ (prom/timer duration-seconds node-name "poll-tx-log")]
    (tx-log/poll! tx-log offset poll-timeout)))

(defn- poll-and-index!
  "Polls `tx-log` once and indexes the resulting transaction data.

  Takes the offset of the next transaction data to index from `state`. Waits
  up to `poll-timeout` for the transaction data to become available."
  [{:keys [node-name] :as node} tx-log state poll-timeout]
  (let [{:keys [t error-t]} @state
        offset (inc (max t error-t))]
    (log/trace "poll transaction data with offset =" offset)
    (index-batch! node (poll-tx-log! node-name tx-log offset poll-timeout))))

(defn- finish-indexing!
  "Marks the indexing of `state` as finished and fails all its waiters, so that
  all futures waiting for a t that will never be reached complete exceptionally
  instead of waiting forever.

  Removes the waiters in the same state change that marks the indexing as
  finished, so that a waiter registering concurrently is either failed here or
  isn't registered at all, because `add-waiter` sees the finished indexing."
  [node-name state]
  (-> (first (swap-vals! state assoc :indexing-finished? true
                         :waiters waiters/empty-waiters))
      :waiters
      (waiters/fail-all!
       #(ba/ex-anom (ba/unavailable (closed-before-indexed-msg node-name %))))))

(defn- index-loop
  "Polls the transaction log of `node` and indexes the resulting transaction
  data, until `node` is closed.

  Records an indexing error in the state, so that all futures waiting on a
  database complete exceptionally, and exits."
  [{:keys [node-name tx-log state poll-timeout index-finished] :as node}]
  (log/trace "enter indexer")
  (try
    (while (:run? @state)
      (poll-and-index! node tx-log state poll-timeout))
    (catch Throwable e
      (fail! node-name state)
      (log/error "Error while indexing:" (ex-message e))
      (log/error e))
    (finally
      (finish-indexing! node-name state)
      (ac/complete! index-finished true)
      ;; wakes up the publishing loop, so that it can publish the remaining
      ;; transactions and exit
      (wake-publish-loop! state)
      (log/trace "exit indexer"))))

(defn- enhance-resource-meta [meta t {:blaze.db.tx/keys [instant]}]
  (-> (or meta #fhir/Meta{})
      (assoc :versionId (type/id (str t)))
      (assoc :lastUpdated (node-util/instant instant))))

(defn- mk-meta [handle tx]
  (assoc (meta handle)
         :blaze.resource/hash (:hash handle)
         :blaze.db/num-changes (:num-changes handle)
         :blaze.db/op (:op handle)
         :blaze.db/tx tx))

(defn- enhance-resource [tx-cache handle resource]
  (let [t (:t handle)
        tx (tx-success/tx tx-cache t)]
    (-> (update resource :meta enhance-resource-meta t tx)
        (with-meta (mk-meta handle tx)))))

(defn- rs-keys-of-non-deleted [resource-handles variant]
  (into [] (comp (remove rh/deleted?) (map #(node-util/rs-key % variant))) resource-handles))

(defn- deleted-resource [{:fhir/keys [type] :keys [id]}]
  {:fhir/type type :id id})

(defn- resource-content-not-found-msg [resource-handle]
  (format "The resource content of `%s/%s` with hash `%s` was not found."
          (name (:fhir/type resource-handle)) (:id resource-handle)
          (:hash resource-handle)))

(defn- resource-content-not-found-anom [resource-handle]
  (ba/not-found (resource-content-not-found-msg resource-handle)
                :blaze.db/resource-handle resource-handle))

(defn- enhance-or-not-found
  "Returns the enhanced `resource` content of `resource-handle` or a not-found
  anomaly if `resource` is nil."
  [tx-cache resource-handle resource]
  (if resource
    (enhance-resource tx-cache resource-handle resource)
    (resource-content-not-found-anom resource-handle)))

(defn- to-resource [tx-cache resources resource-handle variant]
  (enhance-or-not-found
   tx-cache resource-handle
   (if (rh/deleted? resource-handle)
     (deleted-resource resource-handle)
     (get resources (node-util/rs-key resource-handle variant)))))

(defn- get-resource [resource-cache get resource-handle variant]
  (if (rh/deleted? resource-handle)
    (ac/completed-future (deleted-resource resource-handle))
    (get resource-cache (node-util/rs-key resource-handle variant))))

(defn- single-clause-with-code-fn? [codes]
  (fn [clauses]
    (and (= 1 (count clauses)) (contains? codes (:code (ffirst clauses))))))

(defn- compartment-clause-patient-ids
  "Extracts the patient IDs from `clause` simply by scanning trough it's values.

  Cancels extraction at the first value that doesn't contain a valid patient ID."
  {:arglists '([clause])}
  [[{:keys [code]} _ values]]
  (transduce
   (comp
    (map
     (if (= "patient" code)
       (fn [value]
         (if-let [[type id] (fsr/split-literal-ref value)]
           (when (= "Patient" type)
             id)
           (when (re-matches #"[A-Za-z0-9\-\.]{1,64}" value)
             value)))
       (fn [value]
         (when-let [[type id] (fsr/split-literal-ref value)]
           (when (= "Patient" type)
             id)))))
    (halt-when nil?)
    (map codec/id-byte-string))
   conj
   []
   values))

(defn- try-compile-patient-type-query
  "Tries to compile `clauses` into a PatientTypeQuery.

  Queries need, among other things, at least one valid compartment scan clause.

  Return nil if that isn't possible."
  {:arglists '([search-param-registry type clauses])}
  [search-param-registry type {:keys [sort-clause search-clauses]}]
  (when-not sort-clause
    (when-some [codes (sr/patient-compartment-search-param-codes search-param-registry type)]
      (let [{[[compartment-clause] :as compartment-clauses] true other-clauses false}
            (group-by (single-clause-with-code-fn? codes) search-clauses)]
        (when (= 1 (count compartment-clauses))
          (let [[scan-clauses other-clauses] (index/compartment-query-plan* other-clauses)]
            (when (seq scan-clauses)
              (let [patient-ids (compartment-clause-patient-ids compartment-clause)]
                (when (seq patient-ids)
                  (qt/patient-type-query
                   (codec/tid type) patient-ids compartment-clause scan-clauses
                   other-clauses))))))))))

(defn- compile-type-query [search-param-registry type clauses lenient?]
  (-> (index/resolve-search-params search-param-registry type clauses lenient?)
      (ac/then-apply
       (fn [clauses]
         (if (empty? clauses)
           (qt/->EmptyTypeQuery (codec/tid type))
           (or (try-compile-patient-type-query search-param-registry type clauses)
               (qt/->TypeQuery (codec/tid type) clauses)))))))

(defn- compile-system-query [search-param-registry clauses lenient?]
  (do-sync [clauses (index/resolve-search-params search-param-registry "Resource" clauses lenient?)]
    (cond
      (:sort-clause clauses)
      (ba/unsupported "Sort clauses aren't supported in system-level queries.")

      (empty? clauses)
      (qs/->EmptySystemQuery)

      :else
      (qs/system-query clauses))))

(defn- compartment-clauses [search-param-registry code type]
  (let [search-param-codes (sr/compartment-resources search-param-registry code type)]
    (if (seq search-param-codes)
      (index/compartment-clauses search-param-registry code search-param-codes type)
      (ac/completed-future (ba/unsupported (format "Unsupported `%s` compartment query of type `%s`." code type))))))

(defn- compile-compartment-query
  ([search-param-registry code type]
   (do-sync [clauses (compartment-clauses search-param-registry code type)]
     (qc/->CompartmentListQuery code clauses (codec/tid type))))
  ([search-param-registry code type clauses lenient?]
   (-> (index/resolve-search-params search-param-registry type clauses lenient?)
       (ac/then-compose
        (fn [clauses]
          (if (:sort-clause clauses)
            (ac/completed-future (ba/unsupported "Sorting is unsupported in compartment queries."))
            (let [search-clauses (:search-clauses clauses)]
              (if (empty? search-clauses)
                (compile-compartment-query search-param-registry code type)
                (let [[scan-clauses other-clauses] (index/compartment-query-plan* search-clauses)]
                  (if (seq scan-clauses)
                    (ac/completed-future
                     (qc/->CompartmentQuery (codec/c-hash code) (codec/tid type)
                                            scan-clauses other-clauses))
                    (do-sync [clauses (compartment-clauses search-param-registry code type)]
                      (qc/->CompartmentSeekQuery code clauses (codec/tid type) other-clauses))))))))))))

(defn- subset-resource-fn [elements]
  (let [keys (conj (seq elements) :fhir/type :id :meta)]
    (fn [resource]
      (-> (select-keys resource keys)
          (update :meta update :tag conj-vec fu/subsetted)))))

(defn- subset-xf [elements]
  (map (subset-resource-fn elements)))

(defn- compile-system-matcher [search-param-registry clauses]
  (-> (index/resolve-search-params search-param-registry "Resource" clauses false)
      (ac/then-apply (fn [clauses] (batch-db/->Matcher (:search-clauses clauses))))))

(defrecord Node [node-name context tx-log tx-cache kv-store resource-cache
                 resource-store sync-fn search-param-registry resource-indexer
                 index-bounds read-only-matcher acquire-in-flight! state stats
                 poll-timeout queue-capacity index-finished publish-finished]
  np/Node
  (-db [node]
    (db/db node (:t @state)))

  (-sync [node]
    (log/trace "sync on last t")
    (sync-fn node))

  (-sync [node t]
    (log/trace "sync on t =" t)
    (t-future node t #(db/db node %)))

  (-submit-tx [_ tx-ops]
    (log/trace "submit" (count tx-ops) "tx-ops")
    (let [{:keys [run? failed?]} @state]
      (cond
        ;; the node stopped because of an indexing or publishing error, so the
        ;; transaction would never be indexed
        failed? (ac/completed-future (failed-node-anom node-name))

        ;; the node is closing, so its indexing loop is about to stop. Accepting
        ;; the transaction would leave the caller with a result that never
        ;; arrives.
        (not run?) (ac/completed-future (ba/unavailable (closed-node-msg node-name)))

        :else
        (if-ok [_ (validation/validate-ops tx-ops)
                _ (acquire-in-flight!)]
          (let [[tx-cmds entries] (tx/prepare-ops context tx-ops)]
            (-> (rs/put! resource-store entries)
                (ac/then-compose-async
                 (fn [_] (tx-log/submit tx-log tx-cmds entries)))
                (ac/when-complete (fn [t _] (swap! state settle-in-flight t)))))
          ac/completed-future))))

  (-tx-result [node t]
    (log/trace "call tx-result: t =" t)
    (t-future node t (fn [_] (tx/load-tx-result node t))))

  (-subscribe-changes! [node type name subscriber]
    (register-subscriber! node type name subscriber))

  p/Tx
  (-tx [_ t]
    (tx-success/tx tx-cache t))

  p/QueryCompiler
  (-compile-type-query [_ type clauses]
    (compile-type-query search-param-registry type clauses false))

  (-compile-type-query-lenient [_ type clauses]
    (compile-type-query search-param-registry type clauses true))

  (-compile-type-matcher [_ type clauses]
    (do-sync [clauses (index/resolve-search-params search-param-registry type clauses false)]
      (batch-db/->Matcher (:search-clauses clauses))))

  (-compile-system-query [_ clauses]
    (compile-system-query search-param-registry clauses false))

  (-compile-system-query-lenient [_ clauses]
    (compile-system-query search-param-registry clauses true))

  (-compile-system-matcher [_ clauses]
    (compile-system-matcher search-param-registry clauses))

  (-compile-compartment-query [_ code type]
    (compile-compartment-query search-param-registry code type))

  (-compile-compartment-query [_ code type clauses]
    (compile-compartment-query search-param-registry code type clauses false))

  (-compile-compartment-query-lenient [_ code type clauses]
    (compile-compartment-query search-param-registry code type clauses true))

  p/Pull
  (-pull [_ resource-handle variant]
    (do-sync [resource (get-resource resource-cache rc/get resource-handle variant)]
      (enhance-or-not-found tx-cache resource-handle resource)))

  (-pull-content [_ resource-handle variant]
    (do-sync [resource (get-resource resource-cache rc/get resource-handle variant)]
      (or (some-> resource (with-meta (meta resource-handle)))
          (resource-content-not-found-anom resource-handle))))

  (-pull-many [_ resource-handles opts]
    (let [{:keys [variant elements skip-cache-insertion?]
           :or {variant :complete}} opts
          keys (rs-keys-of-non-deleted resource-handles variant)
          multi-get (if skip-cache-insertion?
                      rc/multi-get-skip-cache-insertion
                      rc/multi-get)]
      (do-sync [resources (multi-get resource-cache keys)]
        (into
         []
         (cond-> (comp (map #(to-resource tx-cache resources % variant))
                       (halt-when ba/anomaly?))
           elements (comp (subset-xf elements)))
         resource-handles))))

  (-pull-fn [_ opts]
    (let [{:keys [variant elements skip-cache-insertion?] :or {variant :complete}} opts
          get (if skip-cache-insertion?
                rc/get-skip-cache-insertion
                rc/get)
          subset (some-> elements subset-resource-fn)]
      (fn [resource-handle]
        (do-sync [resource (get-resource resource-cache get resource-handle variant)]
          (cond-> (enhance-or-not-found tx-cache resource-handle resource)
            (and subset resource) subset)))))

  AutoCloseable
  (close [_]
    (log/trace "start closing")
    (stop! state)
    ;; none of the following waits is bounded by a timeout. The indexer thread
    ;; finishes the transaction it is in first, which waits on the resource
    ;; store and so is unbounded with distributed storage. Blaze doesn't
    ;; optimize for a graceful shutdown in every situation, because it has to
    ;; survive being killed anyway. A timeout would only let Integrant shut down
    ;; the stores under still running indexing or subscriber code, which is what
    ;; these waits exist to prevent. See docs/implementation/database.md
    @index-finished
    @publish-finished
    ;; the publishing loop closed all subscriptions before it finished, so their
    ;; threads are about to exit
    (wait-for-subscriptions! state)
    (log/trace "done closing")))

(defn- initial-state
  "Returns the initial state of a node starting from the last t of `kv-store`.

  The state contains the `:run?` flag of the indexing loop, whether the node
  `:failed?`, whether the `:indexing-finished?`, the `:t` of the last indexed
  transaction and the `:error-t` of the last failed one, the number of
  transactions currently `:submitting` and the `:in-flight` t's of the ones
  already submitted but not indexed yet, the `:waiters` for a t to be indexed,
  the `:subscriptions` per type, whether they are `:subscriptions-closed?`, the
  `:running-subscriptions` and the `:publish-future` the publishing loop waits
  on.

  The `:failed?` flag only tells that the node stopped because of an error, not
  which one. That distinguishes a node that failed from one that is closing
  normally, which is all any of its callers needs to know.

  The `:waiters` are held in the state, so that registering a waiter and
  deciding whether it has to be registered at all is a single state change, and
  so that the state change releasing a waiter is the one advancing the t it
  waits for. That way every waiter is completed exactly once: either by the
  transaction reaching its t or by the indexing loop finishing without it.

  The `:subscriptions` are the ones the publishing loop queues handles into,
  the `:running-subscriptions` are the ones whose thread can still run
  subscriber code. Both are entered in the same state change, but they are left
  at different points in time: a subscription leaves the `:subscriptions` as
  soon as nothing may be published to it anymore, which is when its subscriber
  cancelled or failed or when the node closed all of them, and leaves the
  `:running-subscriptions` only after its thread exited. The window between the
  two is exactly what closing the node has to wait for."
  [kv-store]
  {:run? true
   :failed? false
   :indexing-finished? false
   :t (or (tx-success/last-t kv-store) 0)
   :error-t 0
   :submitting 0
   :in-flight (sorted-set)
   :waiters waiters/empty-waiters
   :subscriptions {}
   :subscriptions-closed? false
   :running-subscriptions []
   :publish-future (ac/future)})

(defn- init-msg
  [key {:keys [enforce-referential-integrity]
        :or {enforce-referential-integrity true}}]
  (log/info "Open" (node-util/component-name key "local database node") "with"
            (if enforce-referential-integrity "enabled" "disabled")
            "referential integrity checks"))

(defn- ctx
  [{:keys [enforce-referential-integrity allow-multiple-delete]
    :or {enforce-referential-integrity true
         allow-multiple-delete false}}]
  {:blaze.db/enforce-referential-integrity enforce-referential-integrity
   :blaze.db/allow-multiple-delete allow-multiple-delete})

(def ^:private expected-kv-store-version 0)

(defn- kv-store-version [kv-store]
  (or (some-> (kv/get kv-store :default version/key) version/decode-value) 0))

(def ^:private incompatible-kv-store-version-msg
  "Incompatible index store version %1$d found. This version of Blaze needs
  version %2$d.

  Either use an older version of Blaze which is compatible with index store
  version %1$d or do a database migration described here:

    https://blaze-server.org/database/migration

  ")

(defn- incompatible-kv-store-version-ex [actual-version expected-version]
  (ex-info (format incompatible-kv-store-version-msg actual-version expected-version)
           {:actual-version actual-version :expected-version expected-version}))

(defn- check-version! [kv-store]
  (when (tx-success/last-t kv-store)
    (let [actual-kv-store-version (kv-store-version kv-store)]
      (if (= actual-kv-store-version expected-kv-store-version)
        (log/info "Index store version is" actual-kv-store-version)
        (throw (incompatible-kv-store-version-ex actual-kv-store-version
                                                 expected-kv-store-version))))))

(defn- sync-fn [storage]
  (condp identical? storage
    :distributed
    (fn sync-distributed [^Node node]
      (-> (tx-log/last-t (.-tx_log node))
          (ac/then-compose #(np/-sync node %))))
    (fn sync-standalone [node]
      (ac/completed-future (np/-db node)))))

(defn- initial-plc-index-entries [{:keys [state] :as node}]
  (into
   [(plc/state-index-entry {:type :current})]
   (map (fn [{:keys [id]}] (plc/index-entry (codec/id-byte-string id) (:t @state))))
   (d/type-list (d/db node) "Patient")))

(defn build-patient-last-change-index
  [key {:keys [node-name kv-store] :as node}]
  (let [{:keys [type]} (plc/state kv-store)]
    (when (identical? :building type)
      (log/info "Building PatientLastChange index of" (node-util/component-name key "node"))
      (store-tx-entries! node-name kv-store (initial-plc-index-entries node))
      (log/info (format "Finished building PatientLastChange index of %s." (node-util/component-name key "node"))))))

(defn- compile-read-only-matcher [search-param-registry]
  (ac/join
   (compile-system-matcher
    search-param-registry
    [(into ["_tag"] (map #(str % "|read-only")) (canonical/urls "CodeSystem/AccessControl"))])))

(defmethod m/pre-init-spec :blaze.db/node [_]
  (s/keys
   :req-un
   [:blaze.db/tx-log
    :blaze.db/tx-cache
    :blaze.db/kv-store
    ::resource-indexer
    :blaze.db/resource-cache
    :blaze.db/resource-store
    :blaze.db/search-param-registry
    :blaze/scheduler]
   :opt-un
   [:blaze.db/enforce-referential-integrity
    :blaze.db/allow-multiple-delete
    :blaze.db/max-in-flight-transactions
    ::poll-timeout
    ::queue-capacity]))

(defmethod ig/init-key :blaze.db/node
  [key {:keys [storage tx-log tx-cache kv-store resource-indexer resource-cache
               resource-store search-param-registry scheduler poll-timeout
               queue-capacity max-in-flight-transactions]
        :or {poll-timeout (time/seconds 1)
             queue-capacity 16
             max-in-flight-transactions default-max-in-flight-transactions}
        :as config}]
  (init-msg key config)
  (check-version! kv-store)
  (let [node-name (node-util/node-name key)
        state (atom (initial-state kv-store))
        ;; a volatile is sufficient, because only the indexer thread ever reads
        ;; and writes the stats, so they need visibility but no atomicity
        stats (volatile! (node-stats/init kv-store (:t @state)))
        node (->Node node-name (ctx config) tx-log tx-cache
                     kv-store resource-cache resource-store (sync-fn storage)
                     search-param-registry resource-indexer
                     (node-util/index-bounds
                      (resource-indexer/pool-size resource-indexer))
                     (compile-read-only-matcher search-param-registry)
                     (acquire-in-flight-fn node-name state
                                           max-in-flight-transactions)
                     state
                     stats
                     poll-timeout
                     queue-capacity
                     (ac/future)
                     (ac/future))]
    (when (= :building (:type (plc/state kv-store)))
      (sched/submit scheduler #(build-patient-last-change-index key node)))
    (thread/start-thread! #(index-loop node)
                          (node-util/thread-name key "indexer"))
    (thread/start-thread! #(publish-loop node)
                          (node-util/thread-name key "changed-resources-publisher"))
    node))

(defmethod ig/halt-key! :blaze.db/node
  [key node]
  (log/info "Close" (node-util/component-name key "local database node"))
  (.close ^AutoCloseable node))

(def ^:private publishing-lags-xf
  "Transducer from nodes to the publishing lag of each of their changed
  resources subscriptions, the `:queued` transactions and the `:unexamined`
  distance in t under the `:label-values` of that subscription."
  (mapcat
   (fn [{:keys [node-name state]}]
     ;; the t and the subscriptions come from a single state snapshot, so they
     ;; can't get stale by racing writes of the indexing loop. The state of each
     ;; subscription is read afterwards, so a subscription can have advanced
     ;; past that t already. `sub/unexamined` accounts for that.
     (let [{:keys [t subscriptions]} @state]
       (coll/eduction
        (mapcat
         (fn [[type subscriptions]]
           (map (fn [{:keys [name] :as subscription}]
                  {:label-values [node-name type name]
                   :queued (sub/queued subscription)
                   :unexamined (sub/unexamined subscription t)})
                subscriptions)))
        subscriptions)))))

(defn- publishing-lag-samples
  "Returns the samples of the publishing `lags` with the value under `key`."
  [lags key]
  (mapv (fn [{:keys [label-values] :as lag}]
          {:label-values label-values :value (key lag)})
        lags))

(defmethod m/pre-init-spec ::publishing-lag-collector [_]
  (s/keys :req-un [::nodes]))

(defmethod ig/init-key ::publishing-lag-collector
  [_ {:keys [nodes]}]
  (metrics/collector
    (let [lags (into [] publishing-lags-xf (vals nodes))]
      [(metrics/gauge-metric
        "blaze_db_node_publishing_lag_transactions"
        "The number of transactions whose changed resources were queued for but not yet delivered to a changed resources subscriber."
        ["node" "type" "subscriber"]
        (publishing-lag-samples lags :queued))
       (metrics/gauge-metric
        "blaze_db_node_publishing_lag_t"
        "The distance in t between the last transaction indexed by a node and the point in time up to which transactions were examined for a changed resources subscriber."
        ["node" "type" "subscriber"]
        (publishing-lag-samples lags :unexamined))])))

(derive ::publishing-lag-collector :blaze.metrics/collector)

(reg-collector ::duration-seconds
  duration-seconds)

(reg-collector ::index-batch-transactions
  index-batch-transactions)

(reg-collector ::submit-rejections-total
  submit-rejections-total)

(reg-collector ::transaction-sizes
  verify/transaction-sizes)

(reg-collector ::tx-indexer/duration-seconds
  tx-u/duration-seconds)

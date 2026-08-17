(ns blaze.db.node.waiters
  "This namespace provides the waiters of a database node, the futures waiting
  for a certain t to be indexed.

  The waiters are kept in a map of the t waited for to the future waiting for
  it, sorted by that t. Sorted, because the indexing loop advances t
  monotonically, so the waiters a transaction releases are always a prefix of
  that map. Determining them doesn't depend on the number of waiters still
  waiting, so a transaction doesn't become slower the more of them there are.

  There is only a single future per t, because all callers waiting for the same
  t wait for the same event. With distributed storage every request syncs on the
  last t of the transaction log, so that's the common case. Because that future
  is shared, it's never handed to a caller. Callers only ever receive futures
  derived from it, so that completing or cancelling one of them doesn't affect
  the others."
  (:require
   [blaze.async.comp :as ac]))

(def empty-waiters
  "Waiters without any waiter."
  (sorted-map))

(defn add
  "Returns `waiters` with a waiter for `t` registered.

  Keeps the waiter already registered for `t`, so that a single future is shared
  by all callers waiting for `t`. Creates that future only if there is none, so
  that a caller joining the waiter of another one allocates nothing.

  Because the future is created here, the caller has to read it back from the
  returned waiters. A state change applying this function can be retried, so a
  future created by an attempt that lost isn't registered anywhere."
  [waiters t]
  (if (contains? waiters t)
    waiters
    (assoc waiters t (ac/future))))

(defn- ready
  "Returns the entries of `waiters` waiting for a t of at most `t`."
  [waiters t]
  (subseq waiters <= t))

(defn remove-ready
  "Returns `waiters` without the waiters waiting for a t of at most `t`."
  [waiters t]
  (reduce dissoc waiters (map key (ready waiters t))))

(defn complete-ready!
  "Completes the waiters of `waiters` waiting for a t of at most `reached-t`
  with `t`, the t of the last successfully indexed transaction.

  Completes with `t` and not with `reached-t`, because a failed transaction
  produces no new database value. So a waiter released by the error-t of a
  failed transaction receives the t of the last successful transaction.

  Completes asynchronously, because otherwise all the work depending on the
  waiters would happen on the thread of the indexing loop.

  Returns nil."
  [waiters reached-t t]
  (let [supplier (constantly t)]
    (run! #(ac/complete-async! (val %) supplier) (ready waiters reached-t))))

(defn fail-all!
  "Completes all waiters of `waiters` exceptionally with the error `error-fn`
  returns for the t they wait for.

  Doesn't complete asynchronously, unlike `complete-ready!`, because waiters are
  only failed when the indexing loop is stopping. So there is no indexing loop
  left that had to be kept free of the work depending on them.

  Returns nil."
  [waiters error-fn]
  (run! #(ac/complete-exceptionally! (val %) (error-fn (key %))) waiters))

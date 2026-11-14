(ns blaze.db.impl.fold
  "Functions doing work in parallel on a ForkJoinPool."
  (:import
   [java.util.concurrent Callable ForkJoinPool ForkJoinTask]))

(set! *warn-on-reflection* true)

(defn- submit
  "Submits a task calling `f` to `pool`."
  ^ForkJoinTask [^ForkJoinPool pool f]
  (.submit pool (ForkJoinTask/adapt ^Callable f)))

(defn- join [^ForkJoinTask task]
  (.join task))

(defn- quietly-join [^ForkJoinTask task]
  (.quietlyJoin task))

(defn fold
  "Reduces `coll`, a reducible collection, in parallel on `pool`, a
  ForkJoinPool.

  Reads `coll` on the calling thread in consecutive parts of `n` elements and
  submits a task for each full part as soon as it's read, so that parts are
  reduced while `coll` is still read. Reduces the remaining elements, fewer
  than `n`, on the calling thread. So a collection of fewer than `n` elements
  is reduced on the calling thread only.

  Reduces each part with `reduce-part`, a function of the part as vector
  returning its result. So a part can be reduced with resources of its own,
  like a stateful transducer holding an iterator.

  Combines the results of the parts with `combine`, a function of two results,
  only on the calling thread and from left to right, like
  `(reduce combine (combine) results)`, and not in a tree like
  `clojure.core.reducers/fold`. `(combine)` returns the identity of `combine`,
  which is also the result of an empty collection. So the first argument of
  `combine` is the result of all parts so far, growing with each call, and the
  second one is the result of a single part. That suits a `combine` whose cost
  depends only on the size of its second argument, like `into` on vectors or
  `set/union`, which adds the smaller set to the larger one.

  If reading `coll`, a part or `combine` throws, rethrows only after all
  submitted tasks are done, so that no task outlives the call, for example by
  still reading from a database snapshot that the caller closes afterwards."
  [pool n reduce-part combine coll]
  (let [tasks (volatile! [])]
    (try
      (let [rest-result
            (transduce
             (partition-all n)
             (completing
              (fn [acc part]
                (if (= (long n) (count part))
                  (do (vswap! tasks conj (submit pool #(reduce-part part)))
                      acc)
                  (reduce-part part))))
             (combine)
             coll)]
        (-> (transduce (map join) (completing combine) @tasks)
            (combine rest-result)))
      (catch Throwable e
        (run! quietly-join @tasks)
        (throw e)))))

(defn run-parallel!
  "Calls `f` with each element of `coll` in parallel for its side effects, like
  `run!`, on `pool`, a ForkJoinPool. Returns nil once all calls are done.

  If a call throws, rethrows only after all calls are done, like `fold`, so
  that no call outlives this one."
  [pool f coll]
  (let [tasks (mapv #(submit pool (fn [] (f %))) coll)]
    (try
      (run! join tasks)
      (catch Throwable e
        (run! quietly-join tasks)
        (throw e)))))

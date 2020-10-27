(ns blaze.async.comp
  "This namespace provides functions to work with CompletableFutures.

  https://www.baeldung.com/java-completablefuture"
  (:import
    [java.util.concurrent CompletionStage CompletableFuture]
    [java.util.function BiConsumer Function BiFunction Supplier])
  (:refer-clojure :exclude [future]))


(set! *warn-on-reflection* true)


(defn completable-future? [x]
  (instance? CompletableFuture x))


(defn future
  "Returns a new incomplete CompletableFuture"
  []
  (CompletableFuture.))


(defn completed-future
  "Returns a CompletableFuture that is already completed with `x`."
  [x]
  (CompletableFuture/completedFuture x))


(defn failed-future
  "Returns a CompletableFuture that is already completed exceptionally with the
  exception `e`"
  [e]
  (CompletableFuture/failedFuture e))


(defn all-of
  "Returns a CompletableFuture that is completed when all of `futures` complete.

  If any of the given futures complete exceptionally, then the returned
  CompletableFuture also does so, with a CompletionException holding this
  exception as its cause. Otherwise, the results, if any, of the given
  CompletableFutures are not reflected in the returned CompletableFuture, but
  may be obtained by inspecting them individually.

  If no CompletableFutures are provided, returns a CompletableFuture completed
  with nil."
  [futures]
  (CompletableFuture/allOf (into-array CompletableFuture futures)))


(defn complete!
  "If not already completed, sets the value of `future` to `x`.

  Returns true if this invocation caused `future` to transition to a completed
  state, else false."
  [future x]
  (.complete ^CompletableFuture future x))


(defn complete-on-timeout!
  "Completes `future` with `x` if not otherwise completed before the given
  timeout.

  Returns `future` itself."
  [future x timeout unit]
  (.completeOnTimeout ^CompletableFuture future x timeout unit))


(defn complete-exceptionally!
  "If not already completed, causes invocations of `get` and related methods to
  throw `e`.

  Returns true if this invocation caused `future` to transition to a completed
  state, else false."
  [future e]
  (.completeExceptionally ^CompletableFuture future e))


(defn join
  "Like `clojure.core/deref` but faster."
  [future]
  (.join ^CompletableFuture future))


(defmacro supply
  "Returns a CompletableFuture that is synchronously completed by executing
  `body` on the current thread.

  Returns a failed future if `body` throws an exception."
  [& body]
  `(try
     (completed-future (do ~@body))
     (catch Exception e#
       (failed-future e#))))


(defn supply-async
  "Returns a CompletableFuture that is asynchronously completed by a task
  running in `executor` with the value obtained by calling the function `f`
  with no arguments."
  [f executor]
  (CompletableFuture/supplyAsync
    (reify Supplier
      (get [_]
        (f)))
    executor))


(defn completion-stage? [x]
  (instance? CompletionStage x))


(defn then-apply
  "Returns a CompletionStage that, when `stage` completes normally, is executed
  with `stage`'s result as the argument to the function `f`."
  [stage f]
  (.thenApply
    ^CompletionStage stage
    (reify Function
      (apply [_ x]
        (f x)))))


(defn then-apply-async
  "Returns a CompletionStage that, when `stage` completes normally, is executed
  using the optional `executor`, with `stage`'s result as the argument to the
  function `f`."
  ([stage f]
   (.thenApplyAsync
     ^CompletionStage stage
     (reify Function
       (apply [_ x]
         (f x)))))
  ([stage f executor]
   (.thenApplyAsync
     ^CompletionStage stage
     (reify Function
       (apply [_ x]
         (f x)))
     executor)))


(defn then-compose
  "Returns a CompletionStage that is completed with the same value as the
  CompletionStage returned by the function `f`.

  When `stage` completes normally, the function `f` is invoked with `stage`'s
  result as the argument, returning another CompletionStage.  When that stage
  completes normally, the CompletionStage returned by this method is completed
  with the same value.

  To ensure progress, the function `f` must arrange eventual completion of its
  result."
  [stage f]
  (.thenCompose
    ^CompletionStage stage
    (reify Function
      (apply [_ x]
        (f x)))))


(defn handle
  "Returns a CompletionStage that, when `stage` completes either normally or
  exceptionally, is executed with `stage`'s result and exception as arguments to
  the function `f`.

  When `stage` is complete, the function `f` is invoked with the result (or nil
  if none) and the exception (or nil if none) of `stage` as arguments, and the
  `f`'s result is used to complete the returned stage."
  [stage f]
  (.handle
    ^CompletionStage stage
    (reify BiFunction
      (apply [_ x e]
        (f x e)))))


(defn exceptionally
  "Returns a CompletionStage that, when `stage` completes exceptionally, is
  executed with `stage`'s exception as the argument to the function `f`.
  Otherwise, if `stage` completes normally, then the returned stage also
  completes normally with the same value."
  [stage f]
  (.exceptionally
    ^CompletionStage stage
    (reify Function
      (apply [_ e]
        (f e)))))


(defn when-complete
  "Returns a CompletionStage with the same result or exception as `stage`, that
  executes the given action when `stage` completes.

  When `stage` is complete, the given action is invoked with the result (or nil
  if none) and the exception (or nil if none) of `stage` as arguments. The
  returned stage is completed when the action returns."
  [stage f]
  (.whenComplete
    ^CompletionStage stage
    (reify BiConsumer
      (accept [_ x e]
        (f x e)))))


(defn when-complete-async
  "Returns a CompletionStage with the same result or exception as `stage`, that
  executes the given action using `executor` when `stage` completes.

  When `stage` is complete, the given action is invoked with the result (or nil
  if none) and the exception (or nil if none) of `stage` as arguments. The
  returned stage is completed when the action returns."
  [stage f executor]
  (.whenCompleteAsync
    ^CompletionStage stage
    (reify BiConsumer
      (accept [_ x e]
        (f x e)))
    executor))


(defn ->completable-future [stage]
  (.toCompletableFuture ^CompletionStage stage))

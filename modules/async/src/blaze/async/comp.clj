(ns blaze.async.comp
  "This namespace provides functions to work with CompletableFutures.

  https://www.baeldung.com/java-completablefuture"
  (:refer-clojure :exclude [future])
  (:require
   [blaze.anomaly :as ba]
   [clojure.math :as math]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent CompletionStage CompletableFuture TimeUnit CompletionException]))

(set! *warn-on-reflection* true)

(defn completable-future? [x]
  (instance? CompletableFuture x))

(defn future
  "Returns a new incomplete CompletableFuture"
  []
  (CompletableFuture.))

(defn completed-future
  "Returns a CompletableFuture that is already completed with `x` or completed
  exceptionally if `x` is an anomaly."
  [x]
  (if (ba/anomaly? x)
    (CompletableFuture/failedFuture (ba/ex-anom x))
    (CompletableFuture/completedFuture x)))

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

(defn- supplier [f]
  (fn []
    (ba/throw-when (f))))

(defn complete-async!
  "Completes `future` with the result of `f` invoked with no arguments from an
  asynchronous task using the default executor."
  [future f]
  (.completeAsync ^CompletableFuture future (supplier f)))

(defn or-timeout!
  "Exceptionally completes `future` with a TimeoutException if not otherwise
  completed before `timeout` in `unit`.

  Returns `future` itself."
  [future timeout unit]
  (.orTimeout ^CompletableFuture future timeout unit))

(defn complete-on-timeout!
  "Completes `future` with `x` if not otherwise completed before `timeout` in
  `unit`.

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

(defn delayed-executor
  "Returns a new executor that submits a task to the default executor after
  `delay` in `unit`.

  Each delay commences upon invocation of the returned executor's `execute`
  method."
  [delay unit]
  (CompletableFuture/delayedExecutor delay unit))

(defn join
  "Like `clojure.core/deref` but faster."
  [future]
  (.join ^CompletableFuture future))

(defn done?
  "Returns true if `future` completed either: normally, exceptionally, or via
  cancellation."
  [future]
  (.isDone ^CompletableFuture future))

(defn canceled?
  "Returns true if `future` was cancelled before it completed normally."
  [future]
  (.isCancelled ^CompletableFuture future))

(defn cancel!
  "If not already completed, completes `future` with a CancellationException.

  Dependent CompletableFutures that have not already completed will also
  complete exceptionally, with a CompletionException caused by this
  CancellationException.

  Returns true if `future` is now cancelled."
  [future]
  (.cancel ^CompletableFuture future false))

(defn supply-async
  "Returns a CompletableFuture that is asynchronously completed by a task
  running in `executor` with the value obtained by calling the function `f`
  with no arguments."
  ([f]
   (CompletableFuture/supplyAsync (supplier f)))
  ([f executor]
   (CompletableFuture/supplyAsync (supplier f) executor)))

(defn completion-stage? [x]
  (instance? CompletionStage x))

(defn then-apply
  "Returns a CompletionStage that, when `stage` completes normally, is executed
  with `stage`'s result as the argument to the function `f`."
  [stage f]
  (.thenApply ^CompletionStage stage (comp ba/throw-when f)))

(defn then-apply-async
  "Returns a CompletionStage that, when `stage` completes normally, is executed
  using the optional `executor`, with `stage`'s result as the argument to the
  function `f`."
  ([stage f]
   (.thenApplyAsync ^CompletionStage stage (comp ba/throw-when f)))
  ([stage f executor]
   (.thenApplyAsync ^CompletionStage stage (comp ba/throw-when f) executor)))

(defn then-compose
  "Returns a CompletionStage that is completed with the same value as the
  CompletionStage returned by the function `f`.

  When `stage` completes normally, the function `f` is invoked with `stage`'s
  result as the argument, returning another CompletionStage. When that stage
  completes normally, the CompletionStage returned by this method is completed
  with the same value.

  To ensure progress, the function `f` must arrange eventual completion of its
  result."
  [stage f]
  (.thenCompose ^CompletionStage stage (comp ba/throw-when f)))

(defn then-compose-async
  "Returns a CompletionStage that is completed with the same value as the
  CompletionStage returned by the function `f`, executed using the optional
  `executor`.

  When `stage` completes normally, the function `f` is invoked with `stage`'s
  result as the argument, returning another CompletionStage. When that stage
  completes normally, the CompletionStage returned by this method is completed
  with the same value.

  To ensure progress, the function `f` must arrange eventual completion of its
  result."
  ([stage f]
   (.thenComposeAsync ^CompletionStage stage (comp ba/throw-when f)))
  ([stage f executor]
   (.thenComposeAsync ^CompletionStage stage (comp ba/throw-when f) executor)))

(defprotocol CompletionCause
  (-completion-cause [e]))

(extend-protocol CompletionCause
  CompletionException
  (-completion-cause [e] (.getCause e))
  Throwable
  (-completion-cause [e] e))

(defn- handler [f]
  (fn [x e]
    (ba/throw-when (f x (some-> e -completion-cause ba/anomaly)))))

(defn handle
  "Returns a CompletionStage that, when `stage` completes either normally or
  exceptionally, is executed with `stage`'s result and exception as arguments to
  the function `f`.

  When `stage` is complete, the function `f` is invoked with the result (or nil
  if none) and the anomaly (or nil if none) of `stage` as arguments, and the
  `f`'s result is used to complete the returned stage."
  [stage f]
  (.handle ^CompletionStage stage (handler f)))

(defn handle-async
  "Returns a CompletionStage that, when `stage` completes either normally or
  exceptionally, is executed using `stage`'s default asynchronous execution
  facility, with `stage`'s result and exception as arguments to the function
  `f`.

  When `stage` is complete, the function `f` is invoked with the result (or nil
  if none) and the anomaly (or nil if none) of `stage` as arguments, and the
  `f`'s result is used to complete the returned stage."
  [stage f]
  (.handleAsync ^CompletionStage stage (handler f)))

(defn exceptionally
  "Returns a CompletionStage that, when `stage` completes exceptionally, is
  executed with `stage`'s anomaly as the argument to the function `f`.
  Otherwise, if `stage` completes normally, then the returned stage also
  completes normally with the same value."
  [stage f]
  (.exceptionally
   ^CompletionStage stage
   (fn [e]
     (ba/throw-when (f (ba/anomaly (-completion-cause e)))))))

(defn exceptionally-compose [stage f]
  (-> stage
      (handle
       (fn [_ e]
         (if (nil? e)
           stage
           (f e))))
      (then-compose identity)))

(defn exceptionally-compose-async
  "Returns a CompletionStage that, when `stage` completes exceptionally, is
  composed using the results of the function `f` applied to `stage`'s anomaly,
  using `stage`'s default asynchronous execution facility."
  [stage f]
  (-> stage
      (handle
       (fn [_ e]
         (if (nil? e)
           stage
           (-> stage
               (handle-async
                (fn [_ e]
                  (f e)))
               (then-compose identity)))))
      (then-compose identity)))

(defn when-complete
  "Returns a CompletionStage with the same result or exception as `stage`, that
  runs `f` when `stage` completes.

  When `stage` is complete, the given action is invoked with the result (or nil
  if none) and the anomaly (or nil if none) of `stage` as arguments. The
  returned stage is completed when the action returns."
  [stage f]
  (.whenComplete
   ^CompletionStage stage
   (fn [x e]
     (f x (some-> e -completion-cause ba/anomaly)))))

(defn ->completable-future [stage]
  (.toCompletableFuture ^CompletionStage stage))

(defmacro do-sync
  "Returns a CompletionStage that, when `stage-form` completes normally,
  executes `body` with `stage-forms`'s result bound to `binding-form`."
  [[binding-form stage-form] & body]
  `(then-apply
    ~stage-form
    (fn [~binding-form]
      ~@body)))

(defmacro do-async
  "Returns a CompletionStage that, when `stage-form` completes normally,
  executes `body` with `stage-forms`'s result bound to `binding-form` using
  `stage-forms`'s default asynchronous execution facility."
  [[binding-form stage-form] & body]
  `(then-apply-async
    ~stage-form
    (fn [~binding-form]
      ~@body)))

(defn- retryable? [{::anom/keys [category]}]
  (#{::anom/not-found ::anom/busy} category))

(defn- retry* [future-fn action-name max-retries num-retry]
  (-> (future-fn)
      (exceptionally-compose
       (fn [e]
         (if (and (retryable? e) (< num-retry max-retries))
           (let [delay (* (long (math/pow 2.0 num-retry)) 100)]
             (log/warn (format "Wait %d ms before retrying %s." delay action-name))
             (-> (future)
                 (complete-on-timeout!
                  nil delay TimeUnit/MILLISECONDS)
                 (then-compose
                  (fn [_]
                    (retry* future-fn action-name max-retries (inc num-retry))))))
           e)))))

(defn retry
  "Returns a CompletionStage that, when the CompletionStage as result of calling
  the function `f` with no arguments completes normally will complete with its
  result.

  Otherwise retires by calling `f` again with no arguments. Wait's between
  retries starting with 100 ms growing exponentially.

  Please be aware that `num-retries` shouldn't be higher than the max stack
  depth. Otherwise, the CompletionStage would fail with a StackOverflowException."
  [f action-name num-retries]
  (retry* f action-name num-retries 0))

(defn retry2
  "Returns a CompletionStage that, when the CompletionStage as result of calling
  the function`f` with no arguments completes normally will complete with its
  result.

  Otherwise retires by calling `f` again with no arguments if calling the
  function `retry?` with the anomaly returned by the CompletionStage returns
  true."
  [f retry?]
  (-> (f)
      (exceptionally-compose-async
       (fn [e]
         (if (retry? e)
           (retry2 f retry?)
           (completed-future e))))))

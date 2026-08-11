(ns blaze.test-util
  (:require
   [blaze.anomaly :refer [try-anomaly]]
   [blaze.async.comp :as ac]
   [clojure.pprint :as pprint]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [is]]
   [clojure.test.check :as tc]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [java.nio ByteBuffer]
   [java.util Arrays Locale]
   [java.util.concurrent CountDownLatch]))

(set! *warn-on-reflection* true)

(defn all-ex-data [e]
  (cond-> (ex-data e)
    (ex-message e)
    (assoc :message (ex-message e))
    (ex-data (ex-cause e))
    (assoc :cause-data (all-ex-data (ex-cause e)))))

(defmacro given-thrown [v & body]
  `(given (try ~v (is false) (catch Exception e# (all-ex-data e#)))
     ~@body))

(defmacro given-failed-future
  "Asserts that `future` completes exceptionally, running a given macro with
  `body` on the anomaly of its error."
  [future & body]
  `(given (try-anomaly (ac/join ~future) (is false))
     ~@body))

(defmacro satisfies-prop [num-tests prop]
  `(let [result# (tc/quick-check ~num-tests ~prop)]
     (if (true? (:result result#))
       (is :success)
       (let [error# (:result result#)]
         (if (instance? Throwable error#)
           (test/do-report
            {:type :error
             :message (with-out-str (pprint/pprint result#))
             :expected true
             :actual error#})
           (is (pprint/pprint result#)))))))

(defn ba
  "Creates a byte array from `bytes`."
  [& bytes]
  (byte-array bytes))

(defn bb
  "Creates a byte buffer from `bytes`."
  [& bytes]
  (ByteBuffer/wrap (byte-array bytes)))

(defn bytes=
  "Compares two byte arrays for equivalence."
  {:arglists '([a b])}
  [^bytes a ^bytes b]
  (Arrays/equals a b))

(defn fixture [f]
  (st/instrument)
  (log/set-min-level! :trace)
  (f)
  (st/unstrument))

(defn set-default-locale-english! []
  (Locale/setDefault Locale/ENGLISH))

(defn- thread-name []
  (let [name (.getName (Thread/currentThread))]
    (if (str/starts-with? name "nREPL-session") "nREPL-session" name)))

(defn- output-fn
  ([data] (output-fn nil data))
  ([opts data]
   (let [{:keys [no-stacktrace?]} opts
         {:keys [level ?err msg_ ?ns-str ?file timestamp_ ?line]} data]
     (str
      (force timestamp_) " "
      (force (thread-name)) " "
      (str/upper-case (name level)) " "
      "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str "\n" (log/stacktrace err opts))))))))

(log/merge-config!
 {:timestamp-opts
  {:pattern "HH:mm:ss.SSSX"
   :locale :jvm-default
   :timezone :utc}
  :output-fn output-fn})

(defn submit-blocking-task!
  "Submits a blocking task by calling `submit-fn` with it, returning a no-arg
  release function after that task has started to run.

  The task blocks until the returned release function is called. That way the
  executor the task runs on doesn't terminate, so its termination timeout can be
  tested. Call the release function after the timeout has happened, so the task
  finishes and its thread is freed."
  [submit-fn]
  (let [started (CountDownLatch. 1)
        release (CountDownLatch. 1)]
    (submit-fn
     (fn []
       (.countDown started)
       (.await release)))
    (.await started)
    #(.countDown release)))

(defn permutations [coll]
  (if (empty? coll)
    '(())
    (for [x coll
          perm (permutations (remove #{x} coll))]
      (cons x perm))))

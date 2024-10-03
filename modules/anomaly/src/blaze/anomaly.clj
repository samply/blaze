(ns blaze.anomaly
  (:refer-clojure :exclude [map])
  (:require
   [cognitect.anomalies :as anom]
   [io.aviso.exception :as aviso])
  (:import
   [clojure.lang ExceptionInfo]
   [java.util Map]
   [java.util.concurrent CancellationException ExecutionException TimeoutException]))

(set! *warn-on-reflection* true)

(defn anomaly?
  "Checks whether `x` is an anomaly."
  [x]
  (some? (::anom/category x)))

(defn unavailable?
  "Checks whether `x` is an anomaly of category `unavailable`."
  [x]
  (identical? ::anom/unavailable (::anom/category x)))

(defn interrupted?
  "Checks whether `x` is an anomaly of category `interrupted`."
  [x]
  (identical? ::anom/interrupted (::anom/category x)))

(defn incorrect?
  "Checks whether `x` is an anomaly of category `incorrect`."
  [x]
  (identical? ::anom/incorrect (::anom/category x)))

(defn forbidden?
  "Checks whether `x` is an anomaly of category `forbidden`."
  [x]
  (identical? ::anom/forbidden (::anom/category x)))

(defn unsupported?
  "Checks whether `x` is an anomaly of category `unsupported`."
  [x]
  (identical? ::anom/unsupported (::anom/category x)))

(defn not-found?
  "Checks whether `x` is an anomaly of category `not-found`."
  [x]
  (identical? ::anom/not-found (::anom/category x)))

(defn conflict?
  "Checks whether `x` is an anomaly of category `conflict`."
  [x]
  (identical? ::anom/conflict (::anom/category x)))

(defn fault?
  "Checks whether `x` is an anomaly of category `fault`."
  [x]
  (identical? ::anom/fault (::anom/category x)))

(defn busy?
  "Checks whether `x` is an anomaly of category `busy`."
  [x]
  (identical? ::anom/busy (::anom/category x)))

(defn- anomaly*
  ([category msg]
   (cond-> {::anom/category category} msg (assoc ::anom/message msg)))
  ([category msg kvs]
   (merge (anomaly* category msg) kvs)))

(defn unavailable [msg & {:as kvs}]
  (anomaly* ::anom/unavailable msg kvs))

(defn interrupted
  ([]
   (interrupted nil))
  ([msg & {:as kvs}]
   (anomaly* ::anom/interrupted msg kvs)))

(defn incorrect [msg & {:as kvs}]
  (anomaly* ::anom/incorrect msg kvs))

(defn forbidden [msg & {:as kvs}]
  (anomaly* ::anom/forbidden msg kvs))

(defn unsupported [msg & {:as kvs}]
  (anomaly* ::anom/unsupported msg kvs))

(defn not-found [msg & {:as kvs}]
  (anomaly* ::anom/not-found msg kvs))

(defn conflict
  ([]
   (conflict nil))
  ([msg & {:as kvs}]
   (anomaly* ::anom/conflict msg kvs)))

(defn fault
  ([]
   (fault nil))
  ([msg & {:as kvs}]
   (anomaly* ::anom/fault msg kvs)))

(defn busy
  ([]
   (busy nil))
  ([msg & {:as kvs}]
   (anomaly* ::anom/busy msg kvs)))

(defn- format-exception
  "Formats `e` without any ANSI formatting.

  Used to output stack traces in OperationOutcome's."
  [e]
  (binding [aviso/*fonts* nil]
    (aviso/format-exception e)))

(defprotocol ToAnomaly
  (-anomaly [x]))

(extend-protocol ToAnomaly
  ExecutionException
  (-anomaly [e]
    (-anomaly (ex-cause e)))
  TimeoutException
  (-anomaly [e]
    (busy (.getMessage e)))
  CancellationException
  (-anomaly [e]
    (interrupted (.getMessage e)))
  ExceptionInfo
  (-anomaly [e]
    (cond->
     (merge
      (cond-> {::anom/category ::anom/fault}
        (.getMessage e)
        (assoc ::anom/message (.getMessage e)))
      (.getData e))
      (.getCause e)
      (assoc :blaze.anomaly/cause (-anomaly (.getCause e)))))
  Throwable
  (-anomaly [e]
    (fault (.getMessage e) :stacktrace (format-exception e)))
  Map
  (-anomaly [m]
    (when (anomaly? m)
      m))
  Object
  (-anomaly [_])
  nil
  (-anomaly [_]))

(defn anomaly
  "Coerces `x` to an anomaly.

  Works for exceptions and anomalies itself.

  Returns nil if `x` isn't an anomaly."
  [x]
  (-anomaly x))

(defmacro try-one
  "Applies a try-catch form arround `body` catching exceptions of `type`,
  returning an anomaly with `category` and possible message of the exception."
  [type category & body]
  `(try
     ~@body
     (catch ~type e#
       (cond-> {::anom/category ~category}
         (.getMessage e#)
         (assoc ::anom/message (.getMessage e#))))))

(defmacro try-all [category & body]
  `(try-one Throwable ~category ~@body))

(defmacro try-anomaly
  "Applies a try-catch form arround `body` catching all Throwables and returns
  an anomaly created by the `anomaly` function."
  [& body]
  `(try
     ~@body
     (catch Throwable e#
       (-anomaly e#))))

(defn ex-anom
  "Creates an ExceptionInfo with `anomaly` as data."
  [anomaly]
  (ex-info (::anom/message anomaly) anomaly))

(defn throw-anom
  "Throws an ExceptionInfo build with `ex-anom`."
  [anomaly]
  (throw (ex-anom anomaly)))

(defn throw-when
  "Throws `x` if `x` is an anomaly. Returns `x` otherwise."
  [x]
  (if (anomaly? x)
    (throw-anom x)
    x))

(defmacro when-ok
  "Like `when-let` or `when-some` but tests for anomalies.

  Each binding consists of a binding-form and an expression. The expression is
  evaluated with all upper binding forms in scope and tested for anomalies. If
  an anomaly is detected, it is redurned and subsequent expressions are not
  evaluated. If all expressions evaluate to non-anomalies, the body is evaluated
  with all binding forms in scope."
  {:arglists '([[bindings*] & body])}
  [bindings & body]
  (if (seq bindings)
    (let [[binding-form expr-form & next] bindings]
      `(let [val# ~expr-form]
         (if (::anom/category val#)
           val#
           (let [~binding-form val#]
             (when-ok ~(vec next) ~@body)))))
    `(do ~@body)))

(defmacro if-ok
  "Like `if-let` or `if-some` but tests for anomalies.

  Unlike the normal `if-let` macro, in this macro the `else` branch requires a
  function, which will be called with the anomaly as an argument.

  Each binding consists of a binding-form and an expression. The expression is
  evaluated with all upper binding forms in scope and tested for anomalies. If
  an anomaly is detected, the result of calling the `else` function with it is
  returned and subsequent expressions are not evaluated. If all expressions
  evaluate to non-anomalies, the body is evaluated with all binding forms in
  scope."
  [bindings then else]
  (if (seq bindings)
    (let [[binding-form expr-form & next] bindings]
      `(let [val# ~expr-form]
         (if (::anom/category val#)
           (~else val#)
           (let [~binding-form val#]
             (if-ok ~(vec next) ~then ~else)))))
    then))

(defn map [x f]
  (if (::anom/category x) x (f x)))

(defn exceptionally [x f]
  (if (::anom/category x) (f x) x))

(defn ignore
  "Ignores a possible anomaly, returning nil instead."
  [x]
  (when-not (::anom/category x) x))

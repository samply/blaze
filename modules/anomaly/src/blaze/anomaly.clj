(ns blaze.anomaly
  (:refer-clojure :exclude [map])
  (:require
    [cognitect.anomalies :as anom]))


(defn ex-anom
  "Creates an ExceptionInfo with `message` and an anomaly build from `kvs`,
  `category` and `message` as data."
  [anom]
  (ex-info (::anom/message anom "") anom))


(defn throw-anom
  "Throws an ExceptionInfo build with `ex-anom`."
  [category message & {:as kvs}]
  (throw (ex-anom (assoc kvs ::anom/category category ::anom/message message))))


(defmacro when-ok
  "Like `when-let` or `when-some` but tests for anomalies.

  Each binding consists of a binding-form and an expression. The expression is
  evaluated with all upper binding forms in scope and tested for anomalies. If
  an anomaly is detected, it is redurned and subsequent expressions are not
  evaluated. If all expressions evaluate to non-anomalies, the body is evaluated
  with all binding forms in scope."
  [bindings & body]
  (if (seq bindings)
    (let [[binding-form expr-form & next] bindings]
      `(let [expr# ~expr-form]
         (if (::anom/category expr#)
           expr#
           (let [~binding-form expr#]
             (when-ok ~(vec next) ~@body)))))
    `(do ~@body)))


(defmacro if-failed [[binding-form form] then else]
  `(let [res# ~form]
     (if (::anom/category res#)
       (let [~binding-form res#] ~then)
       ~else)))


(defmacro if-ok [bindings then else]
  (if (seq bindings)
    (let [[binding-form expr-form & next] bindings]
      `(let [expr# ~expr-form]
         (if (::anom/category expr#)
           (~else expr#)
           (let [~binding-form expr#]
             (if-ok ~(vec next) ~then ~else)))))
    then))

(defn conj-anom
  ([xs]
   xs)
  ([xs x]
   (if (::anom/category x)
     (reduced x)
     (conj xs x))))


(defn map [x f]
  (if (::anom/category x) x (f x)))


(defn exceptionally [x f]
  (if (::anom/category x) (f x) x))

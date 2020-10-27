(ns blaze.elm.compiler.function
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.elm.code :as code]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as q]
    [blaze.fhir.spec.type :as type]
    [cognitect.anomalies :as anom])
  (:import
    [clojure.lang IPersistentMap]))


(set! *warn-on-reflection* true)


(defprotocol ToQuantity
  (-to-quantity [x]))


(extend-protocol ToQuantity
  IPersistentMap
  (-to-quantity [m]
    (when-let [value (:value m)]
      (q/quantity value (or (-> m :code type/value) "1"))))

  Object
  (-to-quantity [x]
    (throw-anom
      ::anom/fault
      (format "Can't convert `%s` to quantity." x)))

  nil
  (-to-quantity [_]))


(defrecord ToQuantityFunctionExpression [operand]
  Expression
  (-eval [_ context resource scope]
    (-to-quantity (-eval operand context resource scope))))


(defrecord ToCodeFunctionExpression [operand]
  Expression
  (-eval [_ context resource scope]
    (let [{:keys [system version code]} (-eval operand context resource scope)]
      (code/to-code (type/value system) (type/value version) (type/value code)))))


(defrecord ToDateFunctionExpression [operand]
  Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date (-eval operand context resource scope) now)))


(defrecord ToDateTimeFunctionExpression [operand]
  Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date-time (-eval operand context resource scope) now)))


(defrecord ToStringFunctionExpression [operand]
  Expression
  (-eval [_ context resource scope]
    (str (-eval operand context resource scope))))

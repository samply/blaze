(ns blaze.elm.compiler.function
  (:require
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.protocols :as p]))


(defrecord ToCodeFunctionExpression [operand]
  Expression
  (-eval [_ context resource scope]
    (:Coding/code (-eval operand context resource scope))))


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
    (let [value (-eval operand context resource scope)]
      (str (or (:code/code value) value)))))

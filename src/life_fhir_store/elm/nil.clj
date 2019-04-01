(ns life-fhir-store.elm.nil
  "Implementation of protocols for nil.

  By dispatching on nil, returning nil is very effective and implementations
  on other types don't have to check for nil."
  (:require
    [life-fhir-store.elm.protocols :as p]))


(extend-protocol p/Equal
  nil
  (equal [_ _]))


(extend-protocol p/Equivalent
  nil
  (equivalent [_ y]
    (nil? y)))


(extend-protocol p/Greater
  nil
  (greater [_ _]))


(extend-protocol p/GreaterOrEqual
  nil
  (greater-or-equal [_ _]))


(extend-protocol p/Less
  nil
  (less [_ _]))


(extend-protocol p/LessOrEqual
  nil
  (less-or-equal [_ _]))


(extend-protocol p/NotEqual
  nil
  (not-equal [_ _]))


(extend-protocol p/Abs
  nil
  (abs [_]))


(extend-protocol p/Add
  nil
  (add [_ _]))


(extend-protocol p/Ceiling
  nil
  (ceiling [_]))


(extend-protocol p/Divide
  nil
  (divide [_ _]))


(extend-protocol p/Exp
  nil
  (exp [_]))


(extend-protocol p/Floor
  nil
  (floor [_]))


(extend-protocol p/Log
  nil
  (log [_ _]))


(extend-protocol p/Ln
  nil
  (ln [_]))


(extend-protocol p/Modulo
  nil
  (modulo [_ _]))


(extend-protocol p/Multiply
  nil
  (multiply [_ _]))


(extend-protocol p/Negate
  nil
  (negate [_]))


(extend-protocol p/Power
  nil
  (power [_ _]))


(extend-protocol p/Predecessor
  nil
  (predecessor [_]))


(extend-protocol p/Round
  nil
  (round [_ _]))


(extend-protocol p/Subtract
  nil
  (subtract [_ _]))


(extend-protocol p/Successor
  nil
  (successor [_]))


(extend-protocol p/Truncate
  nil
  (truncate [_]))


(extend-protocol p/TruncatedDivide
  nil
  (truncated-divide [_ _]))


(extend-protocol p/ToDecimal
  nil
  (to-decimal [_]))

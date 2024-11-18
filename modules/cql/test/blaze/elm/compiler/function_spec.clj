(ns blaze.elm.compiler.function-spec
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.function :as function]
   [clojure.spec.alpha :as s]))

(s/fdef function/arity-0
  :args (s/cat :name string? :fn-expr core/expr?)
  :ret core/expr?)

(s/fdef function/arity-1
  :args (s/cat :name string? :fn-expr core/expr? :op-name string? :op core/expr?)
  :ret core/expr?)

(s/fdef function/arity-2
  :args (s/cat :name string? :fn-expr core/expr? :op-name-1 string?
               :op-name-2 string? :op-1 core/expr? :op-2 core/expr?)
  :ret core/expr?)

(s/fdef function/arity-n
  :args (s/cat :name string? :fn-expr core/expr? :op-names (s/coll-of string?)
               :ops (s/+ core/expr?))
  :ret core/expr?)

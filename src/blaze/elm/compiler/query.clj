(ns blaze.elm.compiler.query
  (:require
    [blaze.elm.compiler.protocols :refer [Expression -eval expr?]]
    [clojure.spec.alpha :as s])
  (:import
    [clojure.core Eduction]))


(defrecord EductionQueryExpression [xform source]
  Expression
  (-eval [_ context resource scope]
    (Eduction. (xform context resource scope) (-eval source context resource scope))))


(s/fdef eduction-expr
  :args (s/cat :xform fn? :source expr?))

(defn eduction-expr [xform source]
  (->EductionQueryExpression xform source))

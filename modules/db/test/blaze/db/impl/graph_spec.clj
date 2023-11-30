(ns blaze.db.impl.graph-spec
  (:require
   [blaze.db.impl.graph :as graph]
   [clojure.spec.alpha :as s]))

(defn- all-some-in?
  [container-predicate]
  (every-pred container-predicate (partial every? some?)))

(s/fdef graph/get-reachables
  :args (s/fspec :args (s/cat :node some?)
                 :ret (s/cat :neighbors (all-some-in? vector?)))
  :ret (s/fspec :args (s/cat :nodes (all-some-in? vector?))
                :ret (s/cat :reachables (all-some-in? set?))))

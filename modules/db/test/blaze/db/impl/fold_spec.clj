(ns blaze.db.impl.fold-spec
  (:require
   [blaze.db.impl.fold :as f]
   [blaze.db.impl.fold.spec]
   [clojure.spec.alpha :as s]))

(s/fdef f/fold
  :args (s/cat :pool ::f/pool :n pos-int? :reduce-part ifn? :combine ifn?
               :coll any?)
  :ret any?)

(s/fdef f/run-parallel!
  :args (s/cat :pool ::f/pool :f ifn? :coll (s/coll-of any?))
  :ret nil?)

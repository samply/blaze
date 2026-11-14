(ns blaze.db.impl.fold.spec
  (:require
   [blaze.db.impl.fold :as-alias f]
   [clojure.spec.alpha :as s])
  (:import
   [java.util.concurrent ForkJoinPool]))

(s/def ::f/pool
  #(instance? ForkJoinPool %))

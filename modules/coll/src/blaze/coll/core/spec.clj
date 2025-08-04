(ns blaze.coll.core.spec
  (:require
   [clojure.spec.alpha :as s]))

(set! *warn-on-reflection* true)

(s/def :blaze/comparator
  ifn?)

(s/def :blaze.coll/merge
  ifn?)

(s/def :blaze/sorted-iterable
  #(instance? Iterable %))

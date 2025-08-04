(ns blaze.coll.core-spec
  (:require
   [blaze.coll.core :as coll]
   [blaze.coll.core.spec]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang IReduceInit]))

(s/fdef coll/first
  :args (s/cat :coll (s/nilable #(instance? IReduceInit %)))
  :ret any?)

(s/fdef coll/some
  :args (s/cat :pred ifn? :coll (s/nilable #(instance? IReduceInit %)))
  :ret any?)

(s/fdef coll/empty?
  :args (s/cat :coll (s/nilable #(instance? IReduceInit %)))
  :ret boolean?)

(s/fdef coll/eduction
  :args (s/cat :xform ifn? :coll any?))

(s/fdef coll/count
  :args (s/cat :coll counted?)
  :ret nat-int?)

(s/fdef coll/nth
  :args (s/cat :coll #(instance? IReduceInit %) :i nat-int? :not-found (s/? any?))
  :ret nat-int?)

(s/fdef coll/intersection
  :args (s/cat :comparator :blaze/comparator :merge :blaze.coll/merge
               :colls (s/+ :blaze/sorted-iterable))
  :ret :blaze/sorted-iterable)

(s/fdef coll/union
  :args (s/cat :comparator :blaze/comparator :merge :blaze.coll/merge
               :colls (s/+ :blaze/sorted-iterable))
  :ret :blaze/sorted-iterable)

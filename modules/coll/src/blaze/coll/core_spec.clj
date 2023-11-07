(ns blaze.coll.core-spec
  (:require
    [blaze.coll.core :as coll]
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
  :args (s/cat :xform ifn? :coll any?)
  :ret #(instance? IReduceInit %))


(s/fdef coll/count
  :args (s/cat :coll counted?)
  :ret nat-int?)


(s/fdef coll/nth
  :args (s/cat :coll #(instance? IReduceInit %) :i nat-int? :not-found (s/? any?))
  :ret nat-int?)

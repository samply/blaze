(ns blaze.db.impl.codec.spec
  (:require
    [clojure.spec.alpha :as s]))


(s/def :blaze.db/state
  nat-int?)

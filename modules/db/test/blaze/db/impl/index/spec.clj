(ns blaze.db.impl.index.spec
  (:require
    [clojure.spec.alpha :as s])
  (:import
    [blaze.db.impl.index.resource Hash]))


(s/def :blaze.db/hash
  #(instance? Hash %))

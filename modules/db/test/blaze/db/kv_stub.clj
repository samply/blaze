(ns blaze.db.kv-stub
  (:require
    [blaze.db.kv :as kv]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st])
  (:refer-clojure :exclude [get]))


(defn get
  ([store key res-spec]
   (st/instrument
     [`kv/get]
     {:spec
      {`kv/get
       (s/fspec
         :args (s/cat :kv-store #{store} :key #{key})
         :ret res-spec)}
      :stub
      #{`kv/get}}))
  ([store column-family key res-spec]
   (st/instrument
     [`kv/get]
     {:spec
      {`kv/get
       (s/fspec
         :args (s/cat :kv-store #{store} :column-family #{column-family} :key #{key})
         :ret res-spec)}
      :stub
      #{`kv/get}})))

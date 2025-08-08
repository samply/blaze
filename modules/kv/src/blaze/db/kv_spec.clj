(ns blaze.db.kv-spec
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.coll.spec :as cs]
   [blaze.db.kv :as kv]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef kv/valid?
  :args (s/cat :iter ::kv/iterator)
  :ret boolean?)

(s/fdef kv/seek-to-first!
  :args (s/cat :iter ::kv/iterator))

(s/fdef kv/seek-to-last!
  :args (s/cat :iter ::kv/iterator))

(s/fdef kv/seek!
  :args (s/cat :iter ::kv/iterator :target bytes?))

(s/fdef kv/seek-buffer!
  :args (s/cat :iter ::kv/iterator :target bb/byte-buffer?))

(s/fdef kv/seek-for-prev!
  :args (s/cat :iter ::kv/iterator :target bytes?))

(s/fdef kv/next!
  :args (s/cat :iter ::kv/iterator))

(s/fdef kv/prev!
  :args (s/cat :iter ::kv/iterator))

(s/fdef kv/key
  :args (s/cat :iter ::kv/iterator)
  :ret bytes?)

(s/fdef kv/key!
  :args (s/cat :iter ::kv/iterator :buf bb/byte-buffer?)
  :ret nat-int?)

(s/fdef kv/value
  :args (s/cat :iter ::kv/iterator)
  :ret bytes?)

(s/fdef kv/value!
  :args (s/cat :iter ::kv/iterator :buf bb/byte-buffer?)
  :ret nat-int?)

(s/fdef kv/new-iterator
  :args (s/cat :snapshot ::kv/snapshot :column-family simple-keyword?)
  :ret ::kv/iterator)

(s/fdef kv/snapshot-get
  :args (s/cat :snapshot ::kv/snapshot :column-family simple-keyword?
               :key bytes?)
  :ret (s/nilable bytes?))

(s/fdef kv/new-snapshot
  :args (s/cat :kv-store :blaze.db/kv-store)
  :ret ::kv/snapshot)

(s/fdef kv/get
  :args (s/cat :kv-store :blaze.db/kv-store :column-family simple-keyword? :key bytes?)
  :ret (s/nilable bytes?))

(s/fdef kv/put!
  :args (s/cat :kv-store :blaze.db/kv-store
               :entries (cs/coll-of :blaze.db.kv/put-entry)))

(s/fdef kv/delete!
  :args (s/cat :kv-store :blaze.db/kv-store
               :entries (cs/coll-of :blaze.db.kv/delete-entry)))

(s/fdef kv/write!
  :args (s/cat :kv-store :blaze.db/kv-store
               :entries (cs/coll-of ::kv/write-entry)))

(s/fdef kv/estimate-num-keys
  :args (s/cat :kv-store :blaze.db/kv-store :column-family simple-keyword?)
  :ret (s/or :estimate-num-keys nat-int? :anomaly ::anom/anomaly))

(s/fdef kv/estimate-scan-size
  :args (s/cat :kv-store :blaze.db/kv-store :column-family simple-keyword?
               :key-range ::kv/key-range)
  :ret (s/or :estimate-storage-size nat-int? :anomaly ::anom/anomaly))

(s/fdef kv/compact!
  :args (s/cat :kv-store :blaze.db/kv-store :column-family simple-keyword?))

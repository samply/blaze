(ns blaze.db.impl.iterators-spec
  (:require
   [blaze.byte-buffer :refer [byte-buffer?]]
   [blaze.byte-string :as bs :refer [byte-string?]]
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.iterators :as i]
   [blaze.db.impl.iterators.spec]
   [blaze.db.kv-spec]
   [clojure.spec.alpha :as s]))

(s/fdef i/seek-key
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family (s/? keyword?) :decode fn?
                      :prefix-length nat-int? :target byte-string?)
               (fn [{:keys [prefix-length target]}]
                 (<= prefix-length (bs/size target))))
  :ret any?)

(s/fdef i/seek-key-first
  :args (s/cat :snapshot :blaze.db.kv/snapshot :column-family keyword?
               :decode fn?)
  :ret any?)

(s/fdef i/seek-key-prev
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family (s/? keyword?) :decode fn?
                      :prefix-length nat-int? :target byte-string?)
               (fn [{:keys [prefix-length target]}]
                 (<= prefix-length (bs/size target))))
  :ret any?)

(s/fdef i/seek-value
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family keyword? :decode fn?
                      :prefix-length nat-int? :target byte-string?)
               (fn [{:keys [prefix-length target]}]
                 (<= prefix-length (bs/size target))))
  :ret any?)

(s/fdef i/keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :column-family keyword? :decode fn?
               :start-key byte-string?)
  :ret (cs/coll-of some?))

(s/fdef i/prefix-keys
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family keyword? :decode fn?
                      :prefix-length nat-int? :start-key byte-string?)
               (fn [{:keys [prefix-length start-key]}]
                 (<= prefix-length (bs/size start-key)))))

(s/fdef i/prefix-keys-prev
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family keyword? :decode fn?
                      :prefix-length nat-int? :start-key byte-string?)
               (fn [{:keys [prefix-length start-key]}]
                 (<= prefix-length (bs/size start-key)))))

(s/fdef i/key
  :args (s/cat :entry ::i/entry)
  :ret byte-buffer?)

(s/fdef i/value
  :args (s/cat :entry ::i/entry)
  :ret byte-buffer?)

(s/fdef i/entries
  :args (s/cat :snapshot :blaze.db.kv/snapshot :column-family keyword?
               :start-key (s/? byte-string?)))

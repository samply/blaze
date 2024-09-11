(ns blaze.db.impl.iterators-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string :as bs :refer [byte-string?]]
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.iterators :as i]
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
  :args (s/cat :snapshot :blaze.db.kv/snapshot :column-family simple-keyword?
               :decode fn?)
  :ret any?)

(s/fdef i/seek-value
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family simple-keyword? :decode fn?
                      :prefix-length nat-int? :target byte-string?)
               (fn [{:keys [prefix-length target]}]
                 (<= prefix-length (bs/size target))))
  :ret any?)

(s/fdef i/seek-key-filter
  :args (s/cat :snapshot :blaze.db.kv/snapshot :column-family simple-keyword?
               :seek fn? :matches? fn? :encode fn?
               :values (s/coll-of some? :min-count 1))
  :ret fn?)

(s/fdef i/target-length-matcher
  :args (s/cat :matches? fn?)
  :ret fn?)

(s/fdef i/prefix-length-matcher
  :args (s/cat :prefix-length fn? :matches? fn?)
  :ret fn?)

(s/fdef i/keys
  :args (s/cat :snapshot :blaze.db.kv/snapshot
               :column-family simple-keyword? :decode fn?
               :start-key byte-string?)
  :ret (cs/coll-of some?))

(s/fdef i/prefix-keys
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family simple-keyword? :decode fn?
                      :prefix-length nat-int? :start-key byte-string?)
               (fn [{:keys [prefix-length start-key]}]
                 (<= prefix-length (bs/size start-key)))))

(s/fdef i/prefix-keys-prev
  :args (s/and (s/cat :snapshot :blaze.db.kv/snapshot
                      :column-family simple-keyword? :decode fn?
                      :prefix-length nat-int? :start-key byte-string?)
               (fn [{:keys [prefix-length start-key]}]
                 (<= prefix-length (bs/size start-key)))))

(s/fdef i/entries
  :args (s/cat :snapshot :blaze.db.kv/snapshot :column-family simple-keyword?
               :xform fn? :start-key (s/? byte-string?))
  :ret (cs/coll-of some?))

(s/fdef i/prefix-entries
  :args (s/cat :snapshot :blaze.db.kv/snapshot :column-family simple-keyword?
               :xform fn? :prefix-length nat-int? :start-key byte-string?)
  :ret (cs/coll-of some?))

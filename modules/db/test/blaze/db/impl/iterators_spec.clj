(ns blaze.db.impl.iterators-spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [blaze.byte-string-spec]
    [blaze.coll.core-spec]
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv-spec]
    [clojure.spec.alpha :as s]))


(s/fdef i/iter!
  :args (s/cat :iter :blaze.db/kv-iterator :start-key (s/? byte-string?)))


(s/fdef i/iter-prev!
  :args (s/cat :iter :blaze.db/kv-iterator :start-key byte-string?))


(s/fdef i/keys!
  :args (s/cat :iter :blaze.db/kv-iterator :decode fn? :start-key byte-string?))


(s/fdef i/prefix-keys!
  :args (s/cat :iter :blaze.db/kv-iterator :prefix byte-string?
               :decode fn? :start-key byte-string?))


(s/fdef i/kvs!
  :args (s/cat :iter :blaze.db/kv-iterator :decode fn? :start-key byte-string?))

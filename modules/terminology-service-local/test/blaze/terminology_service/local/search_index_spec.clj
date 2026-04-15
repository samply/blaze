(ns blaze.terminology-service.local.search-index-spec
  (:require
   [blaze.terminology-service.local.search-index :as search-index]
   [clojure.spec.alpha :as s])
  (:import
   [org.apache.lucene.store Directory]))

(s/fdef search-index/build
  :args (s/cat :concepts map?)
  :ret #(instance? Directory %))

(s/fdef search-index/build-with-modules
  :args (s/cat :entries seqable?)
  :ret #(instance? Directory %))

(s/fdef search-index/search
  :args (s/cat :dir #(instance? Directory %)
               :filter-text string?
               :max-results nat-int?
               :codes (s/? (s/nilable (s/coll-of string?)))
               :module-ids (s/? (s/nilable (s/coll-of string?))))
  :ret (s/nilable (s/coll-of string?)))

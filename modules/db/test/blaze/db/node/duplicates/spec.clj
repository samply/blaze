(ns blaze.db.node.duplicates.spec
  (:require
   [blaze.db.node.duplicates :as-alias dup]
   [clojure.spec.alpha :as s]))

(s/def ::dup/cmd
  map?)

(s/def ::dup/idx
  nat-int?)

(s/def ::dup/existing-cmd
  map?)

(s/def ::dup/existing-idx
  nat-int?)

(s/def ::dup/duplicate
  (s/keys :req-un [::dup/cmd ::dup/idx ::dup/existing-cmd ::dup/existing-idx]))

(s/def ::dup/resource-key
  (s/tuple string? string?))

(s/def ::dup/index-key
  (s/or :resource ::dup/resource-key
        :version (s/tuple string? string? nat-int?)))

(s/def ::dup/index-entry
  (s/tuple ::dup/idx ::dup/cmd))

(s/def ::dup/index
  (s/every-kv ::dup/index-key ::dup/index-entry :kind map?))

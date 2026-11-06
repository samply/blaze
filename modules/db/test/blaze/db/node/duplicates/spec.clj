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

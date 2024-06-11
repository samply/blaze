(ns blaze.luid.spec
  (:require
   [blaze.luid :as luid]
   [clojure.spec.alpha :as s]))

(s/def ::luid/generator
  luid/generator?)

(s/def :blaze/luid
  (s/and string? #(re-matches #"[A-Z2-7]{16}" %)))

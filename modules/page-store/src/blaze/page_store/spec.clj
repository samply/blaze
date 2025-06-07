(ns blaze.page-store.spec
  (:require
   [blaze.page-store.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze/page-store
  #(satisfies? p/PageStore %))

(s/def :blaze.page-store/token
  (s/and string? #(= 64 (count %))))

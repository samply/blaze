(ns blaze.page-store.spec
  (:require
   [blaze.page-store.backing-store.protocols :as bsp]
   [blaze.page-store.protocols :as p]
   [clojure.spec.alpha :as s]))

(s/def :blaze/page-store
  #(satisfies? p/PageStore %))

(s/def :blaze.page-store/backing-store
  #(satisfies? bsp/BackingStore %))

(s/def :blaze.page-store/token
  (s/and string? #(re-matches #"[0-9A-F]{64}" %)))

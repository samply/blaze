(ns blaze.page-store.spec
  (:require
    [blaze.page-store.protocols :as p]
    [clojure.spec.alpha :as s])
  (:import
    [java.util Random]))


(s/def :blaze/page-store
  #(satisfies? p/PageStore %))


(s/def :blaze.page-store/secure-rng
  #(instance? Random %))


(s/def :blaze.page-store/token
  (s/and string? #(= 32 (count %))))

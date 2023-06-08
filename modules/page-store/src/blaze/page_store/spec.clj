(ns blaze.page-store.spec
  (:require
    [blaze.page-store.protocols :as p]
    [clojure.spec.alpha :as s])
  (:import
    [java.util Random]))


(defn page-store? [x]
  (satisfies? p/PageStore x))


(s/def :blaze/page-store
  page-store?)


(s/def :blaze.page-store/secure-rng
  #(instance? Random %))


(s/def :blaze.page-store/token
  (s/and string? #(= 32 (count %))))

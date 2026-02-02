(ns blaze.page-store.token
  (:require
   [blaze.page-store.local.hash :as hash]))

(defn- multiple-clauses? [disjunction]
  (sequential? (first disjunction)))

(defn- hash-disjunction [disjunction]
  (if (multiple-clauses? disjunction)
    (mapv hash/hash-clause disjunction)
    [(hash/hash-clause disjunction)]))

(defn generate
  "Calculates a SHA256 hash of `clauses` and returns it as Base16 encoded token,
  a string of length 64."
  [clauses]
  (hash/encode (hash/hash-hashes (mapv hash-disjunction clauses))))

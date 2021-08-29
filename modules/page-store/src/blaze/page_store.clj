(ns blaze.page-store
  "Functions for accessing a store of clauses used for pagination of sensitive
  or too large search criteria."
  (:refer-clojure :exclude [get])
  (:require
    [blaze.page-store.protocols :as p]))


(defn get
  "Returns a CompletableFuture that will complete with the clauses stored under
  `token` or complete exceptionally if no clauses were found."
  [store token]
  (p/-get store token))


(defn put!
  "Returns a CompletableFuture that will complete with a token allowing to
  retrieve `clauses` through `get`."
  [store clauses]
  (p/-put store clauses))

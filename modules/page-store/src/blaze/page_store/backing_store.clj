(ns blaze.page-store.backing-store
  "Functions for accessing a store of clauses that backs a page store.

  A backing store doesn't generate tokens itself. It stores clauses under the
  token given by the page store it backs."
  (:refer-clojure :exclude [get])
  (:require
   [blaze.page-store.backing-store.protocols :as p]))

(defn get
  "Returns a CompletableFuture that will complete with the clauses stored under
  `token` or will complete exceptionally if no clauses were found."
  [store token]
  (p/-get store token))

(defn put!
  "Stores `clauses` under `token`.

  Returns a CompletableFuture that will complete with nil after `clauses` are
  stored."
  [store token clauses]
  (p/-put store token clauses))

(ns blaze.db.resource-store
  "Protocols for a store of resource contents keyed by their hashes."
  (:refer-clojure :exclude [get]))

(defprotocol ResourceStore
  "Resource content access by content-hash."

  (-get [store hash variant])

  (-multi-get [store hashes variant])

  (-put [store entries]))

(defn get
  "Returns a CompletableFuture that will complete with the resource content of
  `hash` in `variant` or will complete with nil if it was not found."
  [store hash variant]
  (-get store hash variant))

(defn multi-get
  "Returns a CompletableFuture that will complete with a map from `hash` to the
  resource content of all found `hashes` in `variant`."
  [store hashes variant]
  (-multi-get store hashes variant))

(defn put!
  "Puts `entries`, a map of resource hashes to resource contents, into `store`.

  Returns a CompletableFuture that will complete exceptionally in case any entry
  could not be stored. The ex-data will contain a set of
  :successfully-stored-hashes."
  [store entries]
  (-put store entries))

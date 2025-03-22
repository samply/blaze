(ns blaze.db.resource-store
  "Protocols for a store of resource contents keyed by their hashes."
  (:refer-clojure :exclude [get]))

(defprotocol ResourceStore
  "Resource content access by content-hash."

  (-get [store key])

  (-multi-get [store key])

  (-put [store entries]))

(defn get
  "Returns a CompletableFuture that will complete with the resource content of
  the resource with `key` or will complete with nil if it was not found.

  The key is a tuple of `type`, `hash` and `variant`."
  [store key]
  (-get store key))

(defn multi-get
  "Returns a CompletableFuture that will complete with a map from `key` to the
  resource content of all found `keys`.

  The key is a tuple of `type`, `hash` and `variant`."
  [store keys]
  (-multi-get store keys))

(defn put!
  "Puts `entries`, a map of resource hashes to resource contents, into `store`.

  Returns a CompletableFuture that will complete exceptionally in case any entry
  could not be stored. The ex-data will contain a set of
  :successfully-stored-hashes."
  [store entries]
  (-put store entries))

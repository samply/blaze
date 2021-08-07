(ns blaze.db.resource-store
  "Protocols for a store of resource contents keyed by their hashes."
  (:refer-clojure :exclude [get]))


(defprotocol ResourceLookup
  "Resource content access by content-hash."

  (-get [lookup hash])

  (-multi-get [lookup hashes]))


(defn get
  "Returns a CompletableFuture that will complete with the resource content of
  `hash` or nil if it was not found."
  [lookup hash]
  (-get lookup hash))


(defn multi-get
  "Returns a CompletableFuture that will complete with a map from `hash` to the
  resource content of all found `hashes`."
  [lookup hashes]
  (-multi-get lookup hashes))


(defprotocol ResourceStore
  "Resource content storage."
  (-put [store entries]))


(defn put!
  "Puts `entries`, a map of resource hashes to resource contents, into `store`.

  Returns a CompletableFuture that will complete exceptionally in case any entry
  could not be stored. The ex-data will contain a set of
  :successfully-stored-hashes."
  [store entries]
  (-put store entries))

(ns blaze.page-store.backing-store.protocols
  "The protocol a backing store of a page store has to implement.

  Use the functions in `blaze.page-store.backing-store` to access a backing
  store.")

(defprotocol BackingStore
  (-get [store token])
  (-put [store token clauses]))

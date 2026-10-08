(ns blaze.page-store.protocols
  "The protocol a page store has to implement.

  Use the functions in `blaze.page-store` to access a page store.")

(defprotocol PageStore
  (-get [store token])
  (-put [store clauses]))

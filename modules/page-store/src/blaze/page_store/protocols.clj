(ns blaze.page-store.protocols)


(defprotocol PageStore
  (-get [store token])
  (-put [store clauses]))

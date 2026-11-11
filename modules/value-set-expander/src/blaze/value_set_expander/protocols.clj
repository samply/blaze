(ns blaze.value-set-expander.protocols)

(defprotocol ValueSetExpander
  (-expand-clauses [expander type clauses])
  (-expand-condition-clauses [expander type clauses]))

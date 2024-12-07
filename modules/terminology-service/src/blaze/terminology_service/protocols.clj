(ns blaze.terminology-service.protocols)

(defprotocol TerminologyService
  (-expand-value-set [_ request]))

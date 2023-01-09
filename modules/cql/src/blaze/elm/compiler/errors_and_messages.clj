(ns blaze.elm.compiler.errors-and-messages
  "24. Errors and Messages

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.elm.compiler.core :as core]))


;; 24.1. Message
(defmethod core/compile* :elm.compiler.type/message
  [context {:keys [source]}]
  ;; TODO
  )

(ns blaze.validator.protocols)

(defprotocol Validator
  (-validate [_ resource]))

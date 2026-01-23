(ns blaze.elm.value-set.protocol)

(defprotocol ValueSet
  (-contains-string [value-set code])
  (-contains-code [value-set code])
  (-contains-concept [value-set concept])
  (-expand [value-set]))

(ns blaze.elm.value-set.protocol)

(defprotocol ValueSet
  (-contains-string [_ code])
  (-contains-code [_ code])
  (-contains-concept [_ concept]))

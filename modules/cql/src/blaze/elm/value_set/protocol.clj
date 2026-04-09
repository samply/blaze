(ns blaze.elm.value-set.protocol)

(defprotocol ValueSet
  (-url [_])
  (-contains-string [_ code])
  (-contains-code [_ code])
  (-contains-concept [_ concept]))

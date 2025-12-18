(ns blaze.elm.code-system.protocol)

(defprotocol CodeSystem
  (-contains-string [_ code])
  (-contains-code [_ code])
  (-contains-concept [_ concept]))

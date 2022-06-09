(ns blaze.elm.tuple
  "A CQL Tuple is implemented by a Clojure map."
  (:require
    [blaze.elm.protocols :as p])
  (:import
    [java.util Map]))

;; 12.1. Equal
(extend-protocol p/Equal
  Map
  (equal [x y]
    (loop [[[k vx] & xs] x]
      (if (some? k)
        (if-let [vy (get y k)]
          (if (p/equal vx vy)
            (recur xs)
            false)
          false)
        true))))

;; 22.23. ToDateTime
(extend-protocol p/ToDateTime
  ;; for the anomaly
  Map
  (to-date-time [_ _]))

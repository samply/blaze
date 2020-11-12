(ns blaze.elm.tuple
  "A CQL Tuple is implemented by a Clojure map."
  (:require
    [blaze.elm.protocols :as p])
  (:import [clojure.lang IPersistentMap]))


(extend-protocol p/Equal
  IPersistentMap
  (equal [x y]
    (loop [[[k vx] & xs] x]
      (if (some? k)
        (if-let [vy (get y k)]
          (if (p/equal vx vy)
            (recur xs)
            false)
          false)
        true))))

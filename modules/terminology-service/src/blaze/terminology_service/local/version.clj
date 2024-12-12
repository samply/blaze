(ns blaze.terminology-service.local.version
  (:require
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn- nat-cmp [^Comparable x y]
  (.compareTo x y))

(defn cmp
  "Compares two version numbers."
  [v1 v2]
  (loop [[p1 & p1s] (some-> v1 (str/split #"\."))
         [p2 & p2s] (some-> v2 (str/split #"\."))]
    (if p1
      (if p2
        (let [n1 (parse-long p1)
              n2 (parse-long p2)]
          (if n1
            (if n2
              (let [r (nat-cmp n1 n2)]
                (if (zero? r)
                  (recur p1s p2s)
                  r))
              -1)
            (if n2
              1
              (let [r (nat-cmp p1 p2)]
                (if (zero? r)
                  (recur p1s p2s)
                  r)))))
        1)
      (if p2
        -1
        0))))

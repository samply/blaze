(ns blaze.elm.string
  "Implementation of the string type."
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.protocols :as p]
   [blaze.fhir.spec.type.system :as system]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

;; 17.1. Combine
(defn combine
  "Like `clojure.string/join` but returns nil on first nil in `source`."
  ([source]
   (loop [sb (StringBuilder.)
          [s & more] source]
     (when s
       (if more
         (recur (.append sb s) more)
         (str (.append sb s))))))
  ([separator source]
   (loop [sb (StringBuilder.)
          [s & more] source]
     (when s
       (if more
         (recur (-> sb (.append s) (.append separator)) more)
         (str (.append sb s)))))))

;; 17.6. Indexer
(extend-protocol p/Indexer
  String
  (indexer [string index]
    (when (and index (<= 0 index) (< index (count string)))
      (.substring string index (inc index)))))

;; 22.19. ToBoolean
(extend-protocol p/ToBoolean
  String
  (to-boolean [s]
    (case (str/lower-case s)
      ("true" "t" "yes" "y" "1") true
      ("false" "f" "no" "n" "0") false
      nil)))

;; 22.22. ToDate
(extend-protocol p/ToDate
  String
  (to-date [s _]
    (ba/ignore (system/parse-date s))))

;; 22.23. ToDateTime
(extend-protocol p/ToDateTime
  String
  (to-date-time [s now]
    (p/to-date-time (ba/ignore (system/parse-date-time s)) now)))

;; 22.24. ToDecimal
(extend-protocol p/ToDecimal
  String
  (to-decimal [s]
    (try
      (p/to-decimal (BigDecimal. s))
      (catch Exception _))))

;; 22.30. ToString
(extend-protocol p/ToString
  String
  (to-string [s]
    (str s)))

;; 22.31. ToTime
(extend-protocol p/ToTime
  String
  (to-time [s _]
    (ba/ignore (system/parse-time s))))

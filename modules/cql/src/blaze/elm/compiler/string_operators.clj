(ns blaze.elm.compiler.string-operators
  "17. String Operators"
  (:require
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defbinop defnaryop defternop defunop]]
    [blaze.elm.protocols :as p]
    [blaze.elm.string :as string]
    [clojure.string :as str]))


(set! *warn-on-reflection* true)


;; 17.1. Combine
(defmethod core/compile* :elm.compiler.type/combine
  [context {:keys [source separator]}]
  (let [source (core/compile* context source)
        separator (some->> separator (core/compile* context))]
    (if separator
      (reify core/Expression
        (-eval [_ context resource scope]
          (when-let [source (core/-eval source context resource scope)]
            (string/combine (core/-eval separator context resource scope)
                            source))))
      (reify core/Expression
        (-eval [_ context resource scope]
          (when-let [source (core/-eval source context resource scope)]
            (string/combine source)))))))


;; 17.2. Concatenate
(defnaryop concatenate [strings]
  (string/combine strings))


;; 17.3. EndsWith
(defbinop ends-with [s suffix]
  (when (and s suffix)
    (str/ends-with? s suffix)))


;; 17.6. Indexer
(defbinop indexer [x index]
  (p/indexer x index))


;; 17.7. LastPositionOf
(defmethod core/compile* :elm.compiler.type/last-position-of
  [context {:keys [pattern string]}]
  (let [pattern (core/compile* context pattern)
        string (core/compile* context string)]
    (reify core/Expression
      (-eval [_ context resource scope]
        (when-let [^String pattern (core/-eval pattern context resource scope)]
          (when-let [^String string (core/-eval string context resource scope)]
            (.lastIndexOf string pattern)))))))


;; 17.8. Length
(defunop length [x]
  (count x))


;; 17.9. Lower
(defunop lower [s]
  (some-> s str/lower-case))


;; 17.10. Matches
(defbinop matches [s pattern]
  (when (and s pattern)
    (some? (re-matches (re-pattern pattern) s))))


;; 17.12. PositionOf
(defmethod core/compile* :elm.compiler.type/position-of
  [context {:keys [pattern string]}]
  (let [pattern (core/compile* context pattern)
        string (core/compile* context string)]
    (reify core/Expression
      (-eval [_ context resource scope]
        (when-let [^String pattern (core/-eval pattern context resource scope)]
          (when-let [^String string (core/-eval string context resource scope)]
            (.indexOf string pattern)))))))


;; 17.13. ReplaceMatches
(defternop replace-matches [s pattern substitution]
  (when (and s pattern substitution)
    (str/replace s (re-pattern pattern) substitution)))


;; 17.14. Split
(defmethod core/compile* :elm.compiler.type/split
  [context {string :stringToSplit :keys [separator]}]
  (let [string (core/compile* context string)
        separator (some->> separator (core/compile* context))]
    (if separator
      (reify core/Expression
        (-eval [_ context resource scope]
          (when-let [string (core/-eval string context resource scope)]
            (if (= "" string)
              [string]
              (if-let [separator (core/-eval separator context resource scope)]
                (condp = (count separator)
                  0
                  [string]
                  1
                  (loop [[char & more] string
                         result []
                         acc (StringBuilder.)]
                    (if (= (str char) separator)
                      (if more
                        (recur more (conj result (str acc)) (StringBuilder.))
                        (conj result (str acc)))
                      (if more
                        (recur more result (.append acc char))
                        (conj result (str (.append acc char))))))
                  ;; TODO: implement split with more than one char.
                  (throw (Exception. "TODO: implement split with separators longer than one char.")))
                [string])))))
      (reify core/Expression
        (-eval [_ context resource scope]
          (when-let [string (core/-eval string context resource scope)]
            [string]))))))


;; 17.16. StartsWith
(defbinop starts-with [s prefix]
  (when (and s prefix)
    (str/starts-with? s prefix)))


;; 17.17. Substring
(defmethod core/compile* :elm.compiler.type/substring
  [context {string :stringToSub start-index :startIndex :keys [length]}]
  (let [string (core/compile* context string)
        start-index (core/compile* context start-index)
        length (some->> length (core/compile* context))]
    (if length
      (reify core/Expression
        (-eval [_ context resource scope]
          (when-let [^String string (core/-eval string context resource scope)]
            (when-let [start-index (core/-eval start-index context resource scope)]
              (when (and (<= 0 start-index) (< start-index (count string)))
                (subs string start-index (min (+ start-index length)
                                              (count string))))))))
      (reify core/Expression
        (-eval [_ context resource scope]
          (when-let [^String string (core/-eval string context resource scope)]
            (when-let [start-index (core/-eval start-index context resource scope)]
              (when (and (<= 0 start-index) (< start-index (count string)))
                (subs string start-index)))))))))


;; 17.18. Upper
(defunop upper [s]
  (some-> s str/upper-case))

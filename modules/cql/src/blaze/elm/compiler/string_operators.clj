(ns blaze.elm.compiler.string-operators
  "17. String Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defbinop defnaryop defternop defunop reify-expr]]
   [blaze.elm.protocols :as p]
   [blaze.elm.string :as string]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

;; 17.1. Combine
(defn combine-op
  ([source]
   (reify-expr core/Expression
     (-attach-cache [_ cache]
       (core/attach-cache-helper combine-op cache source))
     (-resolve-refs [_ expression-defs]
       (combine-op (core/-resolve-refs source expression-defs)))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper combine-op parameters source))
     (-optimize [_ node]
       (core/optimize-helper combine-op node source))
     (-eval [_ context resource scope]
       (when-let [source (core/-eval source context resource scope)]
         (string/combine source)))
     (-form [_]
       (list 'combine (core/-form source)))))
  ([source separator]
   (reify-expr core/Expression
     (-attach-cache [_ cache]
       (core/attach-cache-helper combine-op cache source separator))
     (-resolve-refs [_ expression-defs]
       (combine-op (core/-resolve-refs source expression-defs)
                   (core/-resolve-refs separator expression-defs)))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper combine-op parameters source separator))
     (-optimize [_ node]
       (core/optimize-helper combine-op node source separator))
     (-eval [_ context resource scope]
       (when-let [source (core/-eval source context resource scope)]
         (string/combine (core/-eval separator context resource scope)
                         source)))
     (-form [_]
       (list 'combine (core/-form source) (core/-form separator))))))

(defmethod core/compile* :elm.compiler.type/combine
  [context {:keys [source separator]}]
  (let [source (core/compile* context source)
        separator (some->> separator (core/compile* context))]
    (if separator
      (combine-op source separator)
      (combine-op source))))

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
(defn last-position-of-op [pattern string]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper last-position-of-op cache pattern string))
    (-resolve-refs [_ expression-defs]
      (last-position-of-op (core/-resolve-refs pattern expression-defs)
                           (core/-resolve-refs string expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper last-position-of-op parameters pattern string))
    (-optimize [_ node]
      (core/optimize-helper last-position-of-op node pattern string))
    (-eval [_ context resource scope]
      (when-let [^String pattern (core/-eval pattern context resource scope)]
        (when-let [^String string (core/-eval string context resource scope)]
          (.lastIndexOf string pattern))))
    (-form [_]
      (list 'last-position-of (core/-form pattern) (core/-form string)))))

(defmethod core/compile* :elm.compiler.type/last-position-of
  [context {:keys [pattern string]}]
  (last-position-of-op (core/compile* context pattern)
                       (core/compile* context string)))

;; 17.8. Length
(defunop length [x]
  (long (count x)))

;; 17.9. Lower
(defunop lower [s]
  (some-> s str/lower-case))

;; 17.10. Matches
(defbinop matches [s pattern]
  (when (and s pattern)
    (some? (re-matches (re-pattern pattern) s))))

;; 17.12. PositionOf
(defn position-of-op [pattern string]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper position-of-op cache pattern string))
    (-resolve-refs [_ expression-defs]
      (position-of-op (core/-resolve-refs pattern expression-defs)
                      (core/-resolve-refs string expression-defs)))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper position-of-op parameters pattern string))
    (-optimize [_ node]
      (core/optimize-helper position-of-op node pattern string))
    (-eval [_ context resource scope]
      (when-let [^String pattern (core/-eval pattern context resource scope)]
        (when-let [^String string (core/-eval string context resource scope)]
          (.indexOf string pattern))))
    (-form [_]
      (list 'position-of (core/-form pattern) (core/-form string)))))

(defmethod core/compile* :elm.compiler.type/position-of
  [context {:keys [pattern string]}]
  (position-of-op (core/compile* context pattern)
                  (core/compile* context string)))

;; 17.13. ReplaceMatches
(defternop replace-matches [s pattern substitution]
  (when (and s pattern substitution)
    (str/replace s (re-pattern pattern) substitution)))

;; 17.14. Split
(defn- split-op [string separator]
  (reify-expr core/Expression
    (-attach-cache [_ cache]
      (core/attach-cache-helper split-op cache string separator))
    (-resolve-refs [_ expression-defs]
      (core/resolve-refs-helper split-op expression-defs string separator))
    (-resolve-params [_ parameters]
      (core/resolve-params-helper split-op parameters string separator))
    (-optimize [_ node]
      (core/optimize-helper split-op node string separator))
    (-eval [_ context resource scope]
      (when-let [string (core/-eval string context resource scope)]
        (if (= "" string)
          [string]
          (if-let [separator (core/-eval separator context resource scope)]
            (case (count separator)
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
            [string]))))
    (-form [_]
      (list 'split (core/-form string) (core/-form separator)))))

(defmethod core/compile* :elm.compiler.type/split
  [context {string :stringToSplit :keys [separator]}]
  (let [string (core/compile* context string)
        separator (some->> separator (core/compile* context))]
    (split-op string separator)))

;; 17.16. StartsWith
(defbinop starts-with [s prefix]
  (when (and s prefix)
    (str/starts-with? s prefix)))

;; 17.17. Substring
(defn substring-op
  ([string start-index]
   (reify-expr core/Expression
     (-attach-cache [_ cache]
       (core/attach-cache-helper substring-op cache string start-index))
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper substring-op expression-defs string start-index))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper substring-op parameters string start-index))
     (-optimize [_ node]
       (core/optimize-helper substring-op node string start-index))
     (-eval [_ context resource scope]
       (when-let [^String string (core/-eval string context resource scope)]
         (when-let [start-index (core/-eval start-index context resource scope)]
           (when (and (<= 0 start-index) (< start-index (count string)))
             (subs string start-index)))))
     (-form [_]
       (list 'substring (core/-form string) (core/-form start-index)))))
  ([string start-index length]
   (reify-expr core/Expression
     (-attach-cache [_ cache]
       (core/attach-cache-helper substring-op cache string start-index length))
     (-resolve-refs [_ expression-defs]
       (core/resolve-refs-helper substring-op expression-defs string start-index length))
     (-resolve-params [_ parameters]
       (core/resolve-params-helper substring-op parameters string start-index length))
     (-optimize [_ node]
       (core/optimize-helper substring-op node string start-index length))
     (-eval [_ context resource scope]
       (when-let [^String string (core/-eval string context resource scope)]
         (when-let [start-index (core/-eval start-index context resource scope)]
           (when (and (<= 0 start-index) (< start-index (count string)))
             (subs string start-index (min (+ start-index length)
                                           (count string)))))))
     (-form [_]
       (list 'substring (core/-form string) (core/-form start-index)
             (core/-form length))))))

(defmethod core/compile* :elm.compiler.type/substring
  [context {string :stringToSub start-index :startIndex :keys [length]}]
  (let [string (core/compile* context string)
        start-index (core/compile* context start-index)
        length (some->> length (core/compile* context))]
    (if length
      (substring-op string start-index length)
      (substring-op string start-index))))

;; 17.18. Upper
(defunop upper [s]
  (some-> s str/upper-case))

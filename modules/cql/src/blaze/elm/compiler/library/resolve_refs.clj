(ns blaze.elm.compiler.library.resolve-refs
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.compiler :as c]
   [clojure.string :as str]
   [clojure.walk :as walk]))

(defn- has-refs? [non-refs {:keys [expression]}]
  (identical?
   (walk/postwalk
    #(if (sequential? %)
       (if (and (= 'expr-ref (first %)) (not (non-refs (second %))))
         ::ref
         (some #{::ref} %))
       %)
    (c/form expression))
   ::ref))

(defn- split-by-having-refs [non-refs expression-defs]
  (reduce-kv
   (fn [[with without] name expression-def]
     (if (has-refs? non-refs expression-def)
       [(assoc with name expression-def) without]
       [with (assoc without name expression-def)]))
   [{} {}]
   expression-defs))

(defn resolve-refs* [expr-def without-refs]
  (update expr-def :expression c/resolve-refs without-refs))

(defn unresolvable-msg [with-refs]
  (format "The following expression definitions contain unresolvable references: %s."
          (str/join "," (map key with-refs))))

(defn resolve-refs [non-refs expression-defs]
  (let [[with-refs without-refs] (split-by-having-refs non-refs expression-defs)]
    (cond
      (empty? with-refs)
      without-refs

      (= (count with-refs) (count expression-defs))
      (ba/incorrect (unresolvable-msg with-refs))

      :else
      (let [resolvable-defs (apply dissoc without-refs non-refs)]
        (recur
         non-refs
         (reduce-kv
          (fn [ret name expr-def]
            (assoc ret name (resolve-refs* expr-def resolvable-defs)))
          without-refs
          with-refs))))))

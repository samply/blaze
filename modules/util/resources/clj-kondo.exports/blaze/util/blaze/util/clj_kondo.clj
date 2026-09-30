(ns blaze.util.clj-kondo)

(defmacro condp-identical
  "Mirrors `blaze.util/condp-identical`, so that clj-kondo lints the same
  expansion and reports the unsupported `:>>` clause form as error."
  [expr & clauses]
  (when (some #{:>>} (take-nth 2 (rest clauses)))
    (throw (ex-info "condp-identical doesn't support the :>> clause form of condp." {})))
  (let [e (gensym "expr")
        emit (fn emit [[test result & more :as clauses]]
               (case (count clauses)
                 0 `(throw (IllegalArgumentException. (str "No matching clause: " ~e)))
                 1 test
                 `(if (identical? ~e ~test) ~result ~(emit more))))]
    `(let [~e ~expr]
       ~(emit clauses))))

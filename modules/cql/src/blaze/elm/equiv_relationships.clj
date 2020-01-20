(ns blaze.elm.equiv-relationships
  "Finds relationships (with and without) in queries, that have an equals
  expression resulting in equiv semi-joins and semi-differences."
  (:require
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]
    [cuerdas.core :as str]))


(defmulti find-equiv-rels
  {:arglists '([expression])}
  (fn [{:keys [type]}]
    (assert type)
    (keyword "elm.normalizer.type" (str/kebab type))))


(defn- update-expression-defs [expression-defs]
  (mapv #(update % :expression find-equiv-rels) expression-defs))


(s/fdef find-equiv-rels-library
  :args (s/cat :library :elm/library))

(defn find-equiv-rels-library [library]
  (update-in library [:statements :def] update-expression-defs))


(defmethod find-equiv-rels :default
  [expression]
  expression)


(defmethod find-equiv-rels :elm.normalizer.type/unary-expression
  [expression]
  (update expression :operand find-equiv-rels))


(defmethod find-equiv-rels :elm.normalizer.type/multiary-expression
  [expression]
  (update expression :operand #(mapv find-equiv-rels %)))


(defn split-by-first-equal-expression
  "Searches for the first Equal expression which is a mandatory condition
  (combined with And) and splits the expression tree into that and the rest.

  Returns a map with :equal-expr and :rest-expr. Retains the whole expression in
  :rest-expr if no equal expression is found."
  [{:keys [type] :as expression}]
  (cond
    (= "Equal" type)
    {:equal-expr expression}

    (= "And" type)
    (let [[operand-1 operand-2] (:operand expression)
          {:keys [equal-expr rest-expr]} (split-by-first-equal-expression operand-1)]
      (if equal-expr
        {:equal-expr equal-expr
         :rest-expr
         (if rest-expr
           {:type "And" :operand [rest-expr operand-2]}
           operand-2)}
        (let [{:keys [equal-expr rest-expr]} (split-by-first-equal-expression operand-2)]
          (if equal-expr
            {:equal-expr equal-expr
             :rest-expr
             (if rest-expr
               {:type "And" :operand [operand-1 rest-expr]}
               operand-1)}
            {:rest-expr expression}))))

    :else
    {:rest-expr expression}))


(defn- find-equiv-relationship
  [{such-that :suchThat :as relationship}]
  (let [{:keys [equal-expr rest-expr]} (split-by-first-equal-expression such-that)]
    (if equal-expr
      (cond->
        (-> relationship
            (update :type str "Equiv")
            (assoc :equivOperand (:operand equal-expr)))
        (some? rest-expr)
        (assoc :suchThat rest-expr)
        (nil? rest-expr)
        (dissoc :suchThat))
      relationship)))


(defmethod find-equiv-rels :elm.normalizer.type/query
  [{relationships :relationship :as expression}]
  (assoc expression :relationship (mapv find-equiv-relationship relationships)))


;; 20. List Operators

;; 20.8. Exists
(derive :elm.normalizer.type/exists :elm.normalizer.type/unary-expression)


;; 20.25. SingletonFrom
(derive :elm.normalizer.type/singleton-from :elm.normalizer.type/unary-expression)

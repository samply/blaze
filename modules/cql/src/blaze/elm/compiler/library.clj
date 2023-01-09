(ns blaze.elm.compiler.library
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.cql-translator :as t]
    [blaze.elm.compiler :as compiler]
    [blaze.elm.compiler.function :as function]
    [blaze.elm.deps-infer :as deps-infer]
    [blaze.elm.equiv-relationships :as equiv-relationships]
    [blaze.elm.normalizer :as normalizer]
    [clojure.java.io :as io]))


(defn- compile-expression-def
  "Compiles the expression of `def` in `context`.

  Returns `def` with :expression replaced with the compiled expression or an
  anomaly on errors."
  [context def]
  (let [context (assoc context :eval-context (:context def))]
    (-> (ba/try-anomaly (update def :expression (partial compiler/compile context)))
        (ba/exceptionally #(assoc % :context context :elm/expression (:expression def))))))


(defn- compile-function-def
  "Compiles the function of `def` in `context`.

  Returns `def` with :expression removed and :function added or an anomaly on
  errors."
  [{{{library-name :id} :identifier} :library :as context}
   {:keys [name operand] :as def}]
  (when-ok [{:keys [expression]} (compile-expression-def context def)]
    (let [function (partial function/arity-n library-name name expression
                            (mapv :name operand))]
      (-> (dissoc def :expression)
          (assoc :function function)))))


(defn- compile-function-defs [context]
  (transduce
    (filter (comp #{"FunctionDef"} :type))
    (completing
      (fn [context {:keys [name] :as def}]
        (if-ok [def (compile-function-def context def)]
          (assoc-in context [:function-defs name] def)
          reduced)))
    context
    (-> context :library :statements :def)))


(defn- expression-defs [context]
  (transduce
    (comp (filter (comp #{"ExpressionDef"} :type))
          (map (partial compile-expression-def context))
          (halt-when ba/anomaly?))
    (completing
      (fn [r {:keys [name] :as def}]
        (assoc r name def)))
    {}
    (-> context :library :statements :def)))


(defn- compile-parameter-def
  "Compiles the default value of `parameter-def` in `context` and associates the
  resulting compiled default value under :default to the `parameter-def` which
  itself is returned.

  Returns an anomaly on errors."
  {:arglists '([context expression-def])}
  [context {:keys [default] :as parameter-def}]
  (if (some? default)
    (-> (ba/try-anomaly
          (assoc parameter-def :default (compiler/compile context default)))
        (ba/exceptionally
          #(assoc % :context context :elm/expression default)))
    parameter-def))


(defn- parameter-default-values [context]
  (transduce
    (comp (map (partial compile-parameter-def context))
          (halt-when ba/anomaly?))
    (completing
      (fn [r {:keys [name default]}]
        (assoc r name default)))
    {}
    (-> context :library :parameters :def)))


(def fhir-helpers
  (t/translate (slurp (io/resource "org/hl7/fhir/FHIRHelpers-4.0.0.cql"))))


(defn- library-not-found-anom [context path version]
  (ba/incorrect
    (format "Library `%s` version `%s` not found." path version)
    :context context))


(declare compile-library)


(defn- resolve-include
  [{:keys [node] :as context}
   {:keys [path version] identifier :localIdentifier}]
  (cond
    (and (= "FHIRHelpers" path) (= "4.0.0" version))
    (assoc-in context [:includes identifier] (compile-library node fhir-helpers {}))
    :else
    (reduced (library-not-found-anom context path version))))


(defn- resolve-includes [context]
  (reduce
    resolve-include
    context
    (-> context :library :includes :def)))


(defn compile-library
  "Compiles `library` using `node`.

  There are currently no options."
  [node library opts]
  (let [library (-> library
                    normalizer/normalize-library
                    equiv-relationships/find-equiv-rels-library
                    deps-infer/infer-library-deps)
        context (assoc opts :node node :library library)]
    (when-ok [{:keys [includes] :as context} (resolve-includes context)
              {:keys [function-defs] :as context} (compile-function-defs context)
              expression-defs (expression-defs context)
              parameter-default-values (parameter-default-values context)]
      {:includes includes
       :expression-defs expression-defs
       :function-defs function-defs
       :parameter-default-values parameter-default-values})))

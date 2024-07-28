(ns blaze.elm.compiler.library
  "ELM Library Compiler.

  The provided function should be called in the following order:

  * compile-library: compiles the ELM library into our internal format
  * evaluate the expressions in the Unfiltered context
  * resolve-all-refs: resolve all the expression references so that expressions
      are self contained and all expressions from Unfiltered context are already
      evaluated
  * resolve-params: resolve last, not yet evaluated parameters to get truly
      self contained expressions"
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.elm.compiler :as c]
   [blaze.elm.compiler.function :as function]
   [blaze.elm.compiler.library.resolve-refs :refer [resolve-refs]]
   [blaze.elm.normalizer :as normalizer]))

(defn- compile-expression-def
  "Compiles the expression of `def` in `context`.

  Returns `def` with :expression replaced with the compiled expression or an
  anomaly on errors."
  [context def]
  (let [context (assoc context :eval-context (:context def))]
    (-> (ba/try-anomaly (update def :expression (partial c/compile context)))
        (ba/exceptionally #(assoc % :context context :elm/expression (:expression def))))))

(defn- compile-function-def
  "Compiles the function of `def` in `context`.

  Returns `def` with :expression removed and :function added or an anomaly on
  errors."
  [context {:keys [name operand] :as def}]
  (when-ok [{:keys [expression]} (compile-expression-def context def)]
    (-> (dissoc def :expression)
        (assoc :function (partial function/arity-n name expression (mapv :name operand))))))

(defn- compile-function-defs [context library]
  (transduce
   (filter (comp #{"FunctionDef"} :type))
   (completing
    (fn [context {:keys [name] :as def}]
      (if-ok [def (compile-function-def context def)]
        (assoc-in context [:function-defs name] def)
        reduced)))
   context
   (-> library :statements :def)))

(defn- expression-defs [context library]
  (transduce
   (comp (filter (comp #{"ExpressionDef"} :type))
         (map (partial compile-expression-def context))
         (halt-when ba/anomaly?))
   (completing
    (fn [r {:keys [name] :as def}]
      (assoc r name def)))
   {}
   (-> library :statements :def)))

(defn- compile-parameter-def
  "Compiles the default value of `parameter-def` in `context` and associates the
  resulting compiled default value under :default to the `parameter-def` which
  itself is returned.

  Returns an anomaly on errors."
  {:arglists '([context parameter-def])}
  [context {:keys [default] :as parameter-def}]
  (if (some? default)
    (-> (ba/try-anomaly
         (let [context (assoc context :eval-context "Unfiltered")]
           (assoc parameter-def :default (c/compile context default))))
        (ba/exceptionally
         #(assoc % :context context :elm/expression default)))
    parameter-def))

(defn- parameter-default-values [context library]
  (transduce
   (comp (map (partial compile-parameter-def context))
         (halt-when ba/anomaly?))
   (completing
    (fn [r {:keys [name default]}]
      (assoc r name default)))
   {}
   (-> library :parameters :def)))

(defn- unfiltered-expr-names [expression-defs]
  (into
   #{}
   (keep (fn [[name {:keys [context]}]] (when (= "Unfiltered" context) name)))
   expression-defs))

(defn compile-library
  "Compiles the ELM `library` using `node` into a map of :expression-defs,
  :function-defs and :parameter-default-values.

  There are currently no options."
  [node library opts]
  (let [library (normalizer/normalize-library library)
        context (assoc opts :node node :library library)]
    (when-ok [{:keys [function-defs] :as context} (compile-function-defs context library)
              expression-defs (expression-defs context library)
              expression-defs (resolve-refs (unfiltered-expr-names expression-defs) expression-defs)
              parameter-default-values (parameter-default-values context library)]
      {:expression-defs expression-defs
       :function-defs function-defs
       :parameter-default-values parameter-default-values})))

(defn resolve-all-refs
  "Resolves all expression references in `expression-defs`."
  [expression-defs]
  (resolve-refs #{} expression-defs))

(defn- resolve-params-xf [parameters]
  (map
   (fn [[name expr-def]]
     [name (update expr-def :expression c/resolve-params parameters)])))

(defn resolve-params
  "Resolves `parameters` in `expression-defs`."
  [expression-defs parameters]
  (into {} (resolve-params-xf parameters) expression-defs))

(defn- optimize-xf [node]
  (let [optimize (partial c/optimize node)]
    (map
     (fn [[name expr-def]]
       [name (update expr-def :expression optimize)]))))

(defn optimize
  "Runs optimizations on expressions from `expression-defs` returning new
  expression definitions.

  The expressions should be already self contained. So all refs and params
  should be resolved."
  [node expression-defs]
  (into {} (optimize-xf node) expression-defs))

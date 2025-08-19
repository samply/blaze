(ns blaze.fhir-path
  (:refer-clojure :exclude [compile eval resolve str])
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.coll.core :as coll]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log])
  (:import
   [clojure.lang IReduceInit PersistentVector]
   [com.google.common.base CharMatcher]
   [java.io StringReader]
   [org.antlr.v4.runtime CharStreams CommonTokenStream]
   [org.antlr.v4.runtime.tree TerminalNode]
   [org.cqframework.cql.gen
    fhirpathLexer fhirpathParser
    fhirpathParser$AdditiveExpressionContext
    fhirpathParser$AndExpressionContext
    fhirpathParser$BooleanLiteralContext
    fhirpathParser$DateLiteralContext
    fhirpathParser$DateTimeLiteralContext
    fhirpathParser$EqualityExpressionContext
    fhirpathParser$ExpressionContext
    fhirpathParser$FunctionContext
    fhirpathParser$FunctionInvocationContext
    fhirpathParser$IdentifierContext
    fhirpathParser$IndexerExpressionContext
    fhirpathParser$InvocationExpressionContext
    fhirpathParser$InvocationTermContext
    fhirpathParser$LiteralTermContext
    fhirpathParser$MemberInvocationContext
    fhirpathParser$NullLiteralContext
    fhirpathParser$NumberLiteralContext
    fhirpathParser$ParamListContext
    fhirpathParser$ParenthesizedTermContext
    fhirpathParser$QualifiedIdentifierContext
    fhirpathParser$StringLiteralContext
    fhirpathParser$TermExpressionContext
    fhirpathParser$TypeExpressionContext
    fhirpathParser$TypeSpecifierContext
    fhirpathParser$UnionExpressionContext]))

(set! *warn-on-reflection* true)

(defprotocol Expression
  (-eval [_ context coll]))

;; The compiler can generate static collections that will evaluate to itself
(extend-protocol Expression
  PersistentVector
  (-eval [expr _ _]
    expr))

(defprotocol Resolver
  (-resolve [_ uri]
    "Resolves `uri` into a resource."))

(defn eval
  "Evaluates the FHIRPath expression on `value` with help of `resolver`.

  Returns either a collection of FHIR data or an anomaly in case of errors. The
  type of the FHIR data can be determined by calling `blaze.fhir.spec/fhir-type`
  on it."
  {:arglists '([resolver expr value])}
  [resolver expr value]
  (-> (ba/try-anomaly (-eval expr {:resolver resolver} [value]))
      (ba/exceptionally #(assoc % :expression expr :value value))))

(defn- convertible?
  "See: http://hl7.org/fhirpath/index.html#conversion"
  [type item]
  (if (identical? type (fhir-spec/fhir-type item))
    true
    (case [(fhir-spec/fhir-type item) type]
      ([:fhir/integer :fhir/decimal]
       [:fhir/date :fhir/dateTime])
      true
      false)))

(defn- convert
  "See: http://hl7.org/fhirpath/index.html#conversion"
  [type item]
  (if (identical? type (fhir-spec/fhir-type item))
    item
    (case [(fhir-spec/fhir-type item) type]
      [:fhir/integer :fhir/decimal]
      (BigDecimal/valueOf (long item)))))

(defn- convert-fhir-primitive
  "Converts `x` to a system type if it is a primitive FHIR type.

  See: https://build.fhir.org/fhirpath.html#types"
  [x]
  (cond-> x (fhir-spec/primitive-val? x) (type/value)))

;; See: http://hl7.org/fhirpath/index.html#singleton-evaluation-of-collections
(defn- singleton-evaluation-msg [coll]
  (format "unable to evaluate `%s` as singleton" (pr-str coll)))

(defn- singleton [type coll]
  (case (coll/count coll)
    1 (let [first (coll/nth coll 0)]
        (cond
          (convertible? type first) (convert type first)
          (identical? :fhir/boolean type) true
          :else (throw-anom (ba/incorrect (singleton-evaluation-msg coll)))))

    0 coll

    (throw-anom (ba/incorrect (singleton-evaluation-msg coll)))))

(deftype StartExpression []
  Expression
  (-eval [_ _ coll]
    coll))

(deftype TypedStartExpression [rf]
  Expression
  (-eval [_ _ coll]
    (.reduce ^IReduceInit coll rf [])))

(defn- typed-start-expression [type-name]
  (let [fhir-type (keyword "fhir" type-name)
        pred #(identical? fhir-type (fhir-spec/fhir-type %))]
    (->TypedStartExpression ((filter pred) conj))))

(deftype GetChildrenExpression [f]
  Expression
  (-eval [_ _ coll]
    (.reduce ^IReduceInit coll f [])))

(defn- get-children-expression [key]
  (->GetChildrenExpression
   (fn [res item]
     (let [val (get item key)]
       (cond
         (instance? IReduceInit val) (.reduce ^IReduceInit val conj res)
         (some? val) (conj res val)
         :else res)))))

(deftype InvocationExpression [expression invocation]
  Expression
  (-eval [_ context coll]
    (-eval invocation context (-eval expression context coll))))

(deftype PlusExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (singleton :fhir/string (-eval left-expr context coll))
          right (singleton :fhir/string (-eval right-expr context coll))]
      (cond
        (empty? left) [right]
        (empty? right) [left]
        :else [(str left right)]))))

(defn- is-type-specifier-msg [coll]
  (format "is type specifier with more than one item at the left side `%s`"
          (pr-str coll)))

(deftype IsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context coll]
    (let [coll (-eval expression context coll)]
      (case (coll/count coll)
        0 coll

        1 [(identical? type-specifier (fhir-spec/fhir-type (coll/nth coll 0)))]

        (throw-anom (ba/incorrect (is-type-specifier-msg coll)))))))

(deftype AsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context coll]
    (let [coll (-eval expression context coll)]
      (case (coll/count coll)
        0 []

        1 (if (identical? type-specifier (fhir-spec/fhir-type (coll/nth coll 0)))
            coll
            [])

        ;; HACK: normally multiple items should throw an error. However in R4 many
        ;; FHIRPath expressions of search parameters use the as type specifier wrongly.
        ;; Please remove that hack for R5.
        (filterv #(identical? type-specifier (fhir-spec/fhir-type %)) coll)))))

(deftype UnionExpression [e1 e2]
  Expression
  (-eval [_ context coll]
    (let [c1 (-eval e1 context coll)
          c2 (-eval e2 context coll)]
      (case (coll/count c1)
        0 (case (coll/count c2)
            (0 1) c2
            (vec (set c2)))
        1 (case (coll/count c2)
            0 c1
            1 (if (= c1 c2) c1 (conj c1 (coll/nth c2 0)))
            (vec (conj (set c2) (coll/nth c1 0))))
        (vec (.reduce ^IReduceInit c2 conj (set c1)))))))

(deftype EqualExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (-eval left-expr context coll)
          right (-eval right-expr context coll)]
      (cond
        (or (empty? left) (empty? right)) []
        (not= (count left) (count right)) [false]
        :else
        (loop [[l & ls] left [r & rs] right]
          (if (system/equals (convert-fhir-primitive l) (convert-fhir-primitive r))
            (if (empty? ls)
              [true]
              (recur ls rs))
            [false]))))))

(deftype NotEqualExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (-eval left-expr context coll)
          right (-eval right-expr context coll)]
      (cond
        (or (empty? left) (empty? right)) []
        (not= (count left) (count right)) [false]
        :else
        (loop [[l & ls] left [r & rs] right]
          (if (system/equals (convert-fhir-primitive l) (convert-fhir-primitive r))
            (if (empty? ls)
              [false]
              (recur ls rs))
            [true]))))))

;; See: http://hl7.org/fhirpath/index.html#and
(deftype AndExpression [expr-a expr-b]
  Expression
  (-eval [_ context coll]
    (let [a (singleton :fhir/boolean (-eval expr-a context coll))]
      (if (false? a)
        [false]

        (let [b (singleton :fhir/boolean (-eval expr-b context coll))]
          (cond
            (false? b) [false]
            (and (true? a) (true? b)) [true]
            :else []))))))

(deftype AsFunctionExpression [type-specifier]
  Expression
  (-eval [_ _ coll]
    (case (coll/count coll)
      0 coll

      1 (if (identical? type-specifier (fhir-spec/fhir-type (coll/nth coll 0)))
          coll
          [])

      ;; HACK: normally multiple items should throw an error. However in R4 many
      ;; FHIRPath expressions of search parameters use the as type specifier wrongly.
      ;; Please remove that hack for R5.
      (filterv #(identical? type-specifier (fhir-spec/fhir-type %)) coll))))

(deftype OfTypeFunctionExpression [type-specifier]
  Expression
  (-eval [_ _ coll]
    (filterv #(identical? type-specifier (fhir-spec/fhir-type %)) coll)))

(deftype ExistsFunctionExpression []
  Expression
  (-eval [_ _ coll]
    [(if (empty? coll) false true)]))

(deftype ExistsWithCriteriaFunctionExpression [criteria]
  Expression
  (-eval [_ _ _]
    (throw-anom (ba/unsupported "unsupported `exists` function"))))

(defmulti ^IReduceInit resolve (fn [_ item] (fhir-spec/fhir-type item)))

(defn- resolve* [resolver uri]
  (if-let [resource (-resolve resolver uri)]
    [resource]
    []))

(defmethod resolve :fhir/string [{:keys [resolver]} uri]
  (resolve* resolver (type/value uri)))

(defmethod resolve :fhir/Reference [{:keys [resolver]} {:keys [reference]}]
  (resolve* resolver (type/value reference)))

(defmethod resolve :default [_ item]
  (log/warn (format "Skip resolving %s `%s`." (name (fhir-spec/fhir-type item))
                    (pr-str item)))
  [])

(deftype ResolveFunctionExpression []
  Expression
  (-eval [_ context coll]
    (.reduce ^IReduceInit coll #(.reduce (resolve context %2) conj %1) [])))

(defprotocol FPCompiler
  (-compile [ctx])
  (-compile-as-type-specifier [ctx]))

(defmulti function-expression (fn [name _] name))

(defmethod function-expression "as"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [type-specifier-ctx (some-> paramsCtx (.expression 0))]
    (->AsFunctionExpression (-compile-as-type-specifier type-specifier-ctx))
    (throw-anom (ba/incorrect "missing type specifier in `as` function"))))

;; 5.2. Filtering and projection

;; 5.2.1. where(criteria : expression) : collection

;; See: http://hl7.org/fhirpath/#wherecriteria-expression-collection
(defn- non-boolean-result-msg [x]
  (format "non-boolean result `%s` of type `%s` while evaluating where function criteria"
          (pr-str x) (fhir-spec/fhir-type x)))

(defn- multiple-result-msg [x]
  (format "multiple result items `%s` while evaluating where function criteria"
          (pr-str x)))

(deftype WhereFunctionExpression [where-rf]
  Expression
  (-eval [_ context coll]
    (.reduce ^IReduceInit coll (where-rf context) [])))

(defn- where-function-expr [criteria]
  (->WhereFunctionExpression
   (fn [context]
     ((filter
       (fn [item]
         (let [coll (-eval criteria context [item])]
           (case (coll/count coll)
             0 false

             1 (let [first (coll/nth coll 0)]
                 (if (identical? :system/boolean (system/type first))
                   first
                   (throw-anom (ba/incorrect (non-boolean-result-msg first)))))

             (throw-anom (ba/incorrect (multiple-result-msg coll)))))))
      conj))))

(defmethod function-expression "where"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [criteria-ctx (some-> paramsCtx (.expression 0))]
    (where-function-expr (-compile criteria-ctx))
    (throw-anom (ba/incorrect "missing criteria in `where` function"))))

(defmethod function-expression "ofType"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [type-specifier-ctx (some-> paramsCtx (.expression 0))]
    (->OfTypeFunctionExpression (-compile-as-type-specifier type-specifier-ctx))
    (throw-anom (ba/incorrect "missing type specifier in `as` function"))))

(defmethod function-expression "exists"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [criteria-ctx (some-> paramsCtx (.expression 0))]
    (->ExistsWithCriteriaFunctionExpression (-compile criteria-ctx))
    (->ExistsFunctionExpression)))

;; 5.3. Subsetting

;; 5.3.1. [ index : Integer ] : collection

(deftype IndexerExpression [expression index]
  Expression
  (-eval [_ context coll]
    (let [coll (-eval expression context coll)
          idx (singleton :fhir/integer (-eval index context coll))
          res (coll/nth coll idx nil)]
      (if (nil? res) [] [res]))))

;; 5.3.3. first() : collection

(deftype FirstFunctionExpression []
  Expression
  (-eval [_ _ coll]
    (if (empty? coll) [] [(first coll)])))

(defmethod function-expression "first"
  [_ _]
  (->FirstFunctionExpression))

;; Additional functions (https://www.hl7.org/fhir/fhirpath.html#functions)

(deftype ExtensionFunctionExpression [rf]
  Expression
  (-eval [_ _ coll]
    (.reduce ^IReduceInit coll rf [])))

(defmethod function-expression "extension"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (let [url-pred (set (some-> paramsCtx (.expression 0) -compile))
        xf (comp (mapcat :extension) (filter (comp url-pred :url)))]
    (->ExtensionFunctionExpression (xf conj))))

(defmethod function-expression "resolve"
  [_ _]
  (->ResolveFunctionExpression))

(defmethod function-expression :default
  [name paramsCtx]
  (throw-anom
   (ba/incorrect
    (format "unknown function `%s`" name)
    :name name
    :paramsCtx paramsCtx)))

(def ^:private ^CharMatcher quote-matcher (CharMatcher/is \'))

(extend-protocol FPCompiler
  fhirpathParser$ExpressionContext
  (-compile [ctx]
    (let [token (.getText (.getOffendingToken (.-exception ctx)))]
      (throw-anom (ba/fault (format "Error while parsing token `%s`" token)))))

  fhirpathParser$TermExpressionContext
  (-compile [ctx]
    (-compile (.term ctx)))
  (-compile-as-type-specifier [ctx]
    (-compile-as-type-specifier (.term ctx)))

  fhirpathParser$InvocationExpressionContext
  (-compile [ctx]
    (->InvocationExpression
     (-compile (.expression ctx))
     (-compile (.invocation ctx))))

  fhirpathParser$IndexerExpressionContext
  (-compile [ctx]
    (->IndexerExpression
     (-compile (.expression ctx 0))
     (-compile (.expression ctx 1))))

  fhirpathParser$AdditiveExpressionContext
  (-compile [ctx]
    (case (.getText (.getSymbol ^TerminalNode (.getChild ctx 1)))
      "+"
      (->PlusExpression
       (-compile (.expression ctx 0))
       (-compile (.expression ctx 1)))))

  fhirpathParser$TypeExpressionContext
  (-compile [ctx]
    (case (.getText (.getSymbol ^TerminalNode (.getChild ctx 1)))
      "is"
      (->IsTypeExpression
       (-compile (.expression ctx))
       (-compile (.typeSpecifier ctx)))
      "as"
      (->AsTypeExpression
       (-compile (.expression ctx))
       (-compile (.typeSpecifier ctx)))))

  fhirpathParser$UnionExpressionContext
  (-compile [ctx]
    (let [[e1 e2 :as exprs] (.expression ctx)]
      (when-not (= 2 (count exprs))
        (throw-anom
         (ba/fault
          (format "UnionExpressionContext with %d expressions" (count exprs)))))
      (->UnionExpression (-compile e1) (-compile e2))))

  fhirpathParser$EqualityExpressionContext
  (-compile [ctx]
    (case (.getText (.getSymbol ^TerminalNode (.getChild ctx 1)))
      "="
      (->EqualExpression
       (-compile (.expression ctx 0))
       (-compile (.expression ctx 1)))
      "!="
      (->NotEqualExpression
       (-compile (.expression ctx 0))
       (-compile (.expression ctx 1)))))

  fhirpathParser$AndExpressionContext
  (-compile [ctx]
    (->AndExpression (-compile (.expression ctx 0)) (-compile (.expression ctx 1))))

  fhirpathParser$InvocationTermContext
  (-compile [ctx]
    (-compile (.invocation ctx)))
  (-compile-as-type-specifier [ctx]
    (-compile-as-type-specifier (.invocation ctx)))

  fhirpathParser$LiteralTermContext
  (-compile [ctx]
    (-compile (.literal ctx)))

  fhirpathParser$ParenthesizedTermContext
  (-compile [ctx]
    (-compile (.expression ctx)))

  fhirpathParser$NullLiteralContext
  (-compile [_]
    [])

  fhirpathParser$BooleanLiteralContext
  (-compile [ctx]
    [(Boolean/valueOf (.getText (.getSymbol ^TerminalNode (.getChild ctx 0))))])

  fhirpathParser$StringLiteralContext
  (-compile [ctx]
    [(.trimFrom quote-matcher (.getText (.getSymbol (.STRING ctx))))])

  fhirpathParser$NumberLiteralContext
  (-compile [ctx]
    (let [text (.getText (.getSymbol (.NUMBER ctx)))]
      [(try
         (Integer/parseInt text)
         (catch NumberFormatException _
           (BigDecimal. text)))]))

  fhirpathParser$DateLiteralContext
  (-compile [ctx]
    (let [text (subs (.getText (.getSymbol (.DATE ctx))) 1)]
      [(system/parse-date text)]))

  fhirpathParser$DateTimeLiteralContext
  (-compile [ctx]
    (let [text (subs (.getText (.getSymbol (.DATETIME ctx))) 1)
          text (if (str/ends-with? text "T") (subs text 0 (dec (count text))) text)]
      [(system/parse-date-time text)]))

  fhirpathParser$MemberInvocationContext
  (-compile [ctx]
    (let [[first-char :as identifier] (-compile (.identifier ctx))]
      (if (Character/isUpperCase ^char first-char)
        (if (= "Resource" identifier)
          (->StartExpression)
          (typed-start-expression identifier))
        (get-children-expression (keyword identifier)))))
  (-compile-as-type-specifier [ctx]
    (let [type (-compile (.identifier ctx))]
      (if (fhir-spec/type-exists? type)
        (keyword "fhir" type)
        (throw-anom (ba/incorrect (format "unknown FHIR type `%s`" type))))))

  fhirpathParser$FunctionInvocationContext
  (-compile [ctx]
    (-compile (.function ctx)))

  fhirpathParser$FunctionContext
  (-compile [ctx]
    (function-expression
     (-compile (.identifier ctx))
     (.paramList ctx)))

  fhirpathParser$TypeSpecifierContext
  (-compile [ctx]
    (let [type (first (-compile (.qualifiedIdentifier ctx)))]
      (if (fhir-spec/type-exists? type)
        (keyword "fhir" type)
        (throw-anom (ba/incorrect (format "unknown FHIR type `%s`" type))))))

  fhirpathParser$QualifiedIdentifierContext
  (-compile [ctx]
    (mapv -compile (.identifier ctx)))

  fhirpathParser$IdentifierContext
  (-compile [ctx]
    (when-let [^TerminalNode node (.getChild ctx 0)]
      (.getText (.getSymbol node)))))

(defn compile
  "Compiles the FHIRPath `expr`.

  Returns either a compiled FHIRPath expression or an anomaly in case of errors."
  [expr]
  (let [r (StringReader. expr)
        s (CharStreams/fromReader r)
        l (fhirpathLexer. s)
        t (CommonTokenStream. l)
        p (fhirpathParser. t)]
    ;; if not removed, it will print errors on console
    (.removeErrorListeners l)
    (.removeErrorListeners p)
    (-> (ba/try-anomaly (-compile (.expression p)))
        (ba/exceptionally
         #(-> (update % ::anom/message str (format " in expression `%s`" expr))
              (assoc :expression expr))))))

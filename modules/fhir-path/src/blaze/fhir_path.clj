(ns blaze.fhir-path
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str]
    [taoensso.timbre :as log])
  (:import
    [clojure.lang ExceptionInfo]
    [java.io StringReader]
    [org.antlr.v4.runtime ANTLRInputStream CommonTokenStream]
    [org.antlr.v4.runtime.tree TerminalNode]
    [org.cqframework.cql.gen
     fhirpathLexer fhirpathParser
     fhirpathParser$TermExpressionContext
     fhirpathParser$InvocationExpressionContext
     fhirpathParser$IndexerExpressionContext
     fhirpathParser$AdditiveExpressionContext
     fhirpathParser$TypeExpressionContext
     fhirpathParser$UnionExpressionContext
     fhirpathParser$EqualityExpressionContext
     fhirpathParser$AndExpressionContext
     fhirpathParser$InvocationTermContext
     fhirpathParser$LiteralTermContext
     fhirpathParser$ParenthesizedTermContext
     fhirpathParser$NullLiteralContext
     fhirpathParser$BooleanLiteralContext
     fhirpathParser$StringLiteralContext
     fhirpathParser$NumberLiteralContext
     fhirpathParser$DateTimeLiteralContext
     fhirpathParser$MemberInvocationContext
     fhirpathParser$FunctionInvocationContext
     fhirpathParser$FunctionContext
     fhirpathParser$ParamListContext
     fhirpathParser$TypeSpecifierContext
     fhirpathParser$QualifiedIdentifierContext
     fhirpathParser$IdentifierContext])
  (:refer-clojure :exclude [eval compile resolve]))


(set! *warn-on-reflection* true)


(defprotocol Expression
  (-eval [_ context coll]))


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
  (try
    (-eval expr {:resolver resolver} [value])
    (catch ExceptionInfo e
      (ex-data e))))


;; See: http://hl7.org/fhirpath/index.html#conversion
(defn- convertible? [type item]
  (if (= (fhir-spec/fhir-type item) type)
    true
    (case [(fhir-spec/fhir-type item) type]
      ([:fhir/integer :fhir/decimal]
       [:fhir/date :fhir/dateTime])
      true
      false)))


;; See: http://hl7.org/fhirpath/index.html#conversion
(defn- convert [type item]
  (if (= (fhir-spec/fhir-type item) type)
    item
    (case [(fhir-spec/fhir-type item) type]
      [:fhir/integer :fhir/decimal]
      (BigDecimal/valueOf (long item))

      [:fhir/date :fhir/dateTime]
      (fhir-spec/to-date-time item))))


;; See: http://hl7.org/fhirpath/index.html#singleton-evaluation-of-collections
(defn- singleton [type coll]
  (cond
    (and (= 1 (count coll)) (convertible? type (first coll)))
    (convert type (first coll))

    (and (= 1 (count coll)) (= :fhir/boolean type))
    true

    (empty? coll)
    []

    :else
    (throw-anom ::anom/incorrect (format "unable to evaluate `%s` as singleton" (pr-str coll)))))


(defrecord StartExpression []
  Expression
  (-eval [_ _ coll]
    coll))


(defrecord TypedStartExpression [spec]
  Expression
  (-eval [_ _ coll]
    (filterv #(= spec (fhir-spec/fhir-type %)) coll)))


(defrecord GetChildrenExpression [key]
  Expression
  (-eval [_ _ coll]
    (reduce
      (fn [res x]
        (let [val (get x key)]
          (cond
            (sequential? val) (into res val)
            (some? val) (conj res val)
            :else res)))
      []
      coll)))


(defrecord InvocationExpression [expression invocation]
  Expression
  (-eval [_ context coll]
    (-eval invocation context (-eval expression context coll))))


(defrecord IndexerExpression [expression index]
  Expression
  (-eval [_ context coll]
    (nth (-eval expression context coll) (-eval index context coll))))


(defrecord PlusExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (singleton :fhir/string (-eval left-expr context coll))
          right (singleton :fhir/string (-eval right-expr context coll))]
      (cond
        (empty? left) [right]
        (empty? right) [left]
        :else [(str left right)]))))


(defrecord IsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context coll]
    (let [coll (-eval expression context coll)]
      (cond
        (= 1 (count coll))
        [(if (= type-specifier (fhir-spec/fhir-type (first coll))) true false)]

        (empty? coll)
        []

        :else
        (throw-anom ::anom/incorrect (format "is type specifier with more than one item at the left side `%s`" (pr-str coll)))))))


(defrecord AsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context x]
    (filterv #(= type-specifier (fhir-spec/fhir-type %)) (-eval expression context x))))


(defrecord UnionExpression [expressions]
  Expression
  (-eval [_ context x]
    (into #{} (mapcat #(-eval % context x)) expressions)))


(defrecord EqualExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (-eval left-expr context coll)
          right (-eval right-expr context coll)]
      (cond
        (or (empty? left) (empty? right)) []
        (not= (count left) (count right)) [false]
        :else
        (loop [[l & ls] left [r & rs] right]
          (if (system/equals (type/value l) (type/value r))
            (if (empty? ls)
              [true]
              (recur ls rs))
            [false]))))))


(defrecord NotEqualExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (-eval left-expr context coll)
          right (-eval right-expr context coll)]
      (cond
        (or (empty? left) (empty? right)) []
        (not= (count left) (count right)) [false]
        :else
        (loop [[l & ls] left [r & rs] right]
          (if (system/equals (type/value l) (type/value r))
            (if (empty? ls)
              [false]
              (recur ls rs))
            [true]))))))


;; See: http://hl7.org/fhirpath/index.html#and
(defrecord AndExpression [expr-a expr-b]
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


(defrecord AsFunctionExpression [type-specifier]
  Expression
  (-eval [_ _ x]
    (filterv #(= type-specifier (fhir-spec/fhir-type %)) x)))


;; See: http://hl7.org/fhirpath/#wherecriteria-expression-collection
(defrecord WhereFunctionExpression [criteria]
  Expression
  (-eval [_ context coll]
    (filterv
      (fn [item]
        (let [res (-eval criteria context [item])]
          (cond
            (= 1 (count res))
            (if (identical? :system/boolean (system/type (first res)))
              (first res)
              (throw-anom ::anom/incorrect (format "non-boolean result `%s` of type `%s` while evaluating where function criteria" (pr-str (first res)) (fhir-spec/fhir-type (first res)))))

            (empty? res)
            false

            :else
            (throw-anom ::anom/incorrect "multiple result items `%s` while evaluating where function criteria" (pr-str res)))))
      coll)))


(defrecord ExistsFunctionExpression []
  Expression
  (-eval [_ _ coll]
    [(if (empty? coll) false true)]))


(defrecord ExistsWithCriteriaFunctionExpression [criteria]
  Expression
  (-eval [_ _ _]
    (throw-anom ::anom/unsupported "unsupported `exists` function")))


(defmulti resolve (fn [_ item] (fhir-spec/fhir-type item)))


(defn- resolve* [resolver uri]
  (when-let [resource (-resolve resolver uri)]
    [resource]))


(defmethod resolve :fhir/string [{:keys [resolver]} uri]
  (resolve* resolver uri))


(defmethod resolve :fhir/Reference [{:keys [resolver]} {:keys [reference]}]
  (resolve* resolver reference))


(defmethod resolve :default [_ item]
  (log/debug (format "Skip resolving %s `%s`." (name (fhir-spec/fhir-type item)) (pr-str item))))


(defrecord ResolveFunctionExpression []
  Expression
  (-eval [_ context coll]
    (mapcat (partial resolve context) coll)))


(defrecord LiteralExpression [literal]
  Expression
  (-eval [_ _ _]
    [literal]))


(defrecord NullLiteralExpression []
  Expression
  (-eval [_ _ _]
    []))


(defprotocol FPCompiler
  (-compile [ctx])
  (-compile-as-type-specifier [ctx]))


(defmulti function-expression (fn [name _] name))


(defmethod function-expression "as"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [type-specifier-ctx (some-> paramsCtx (.expression 0))]
    (->AsFunctionExpression (-compile-as-type-specifier type-specifier-ctx))
    (throw-anom ::anom/incorrect "missing type specifier in `as` function")))


(defmethod function-expression "where"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [criteria-ctx (some-> paramsCtx (.expression 0))]
    (->WhereFunctionExpression (-compile criteria-ctx))
    (throw-anom ::anom/incorrect "missing criteria in `where` function")))


(defmethod function-expression "exists"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [criteria-ctx (some-> paramsCtx (.expression 0))]
    (->ExistsWithCriteriaFunctionExpression (-compile criteria-ctx))
    (->ExistsFunctionExpression)))


;; See: https://hl7.org/fhir/fhirpath.html#functions
(defmethod function-expression "resolve"
  [_ _]
  (->ResolveFunctionExpression))


(defmethod function-expression :default
  [name paramsCtx]
  (throw-anom
    ::anom/incorrect (format "unknown function `%s`" name)
    :name name
    :paramsCtx paramsCtx))


(extend-protocol FPCompiler
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
    (->UnionExpression (mapv -compile (.expression ctx))))

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
    (->NullLiteralExpression))

  fhirpathParser$BooleanLiteralContext
  (-compile [ctx]
    (->LiteralExpression
      (Boolean/valueOf (.getText (.getSymbol ^TerminalNode (.getChild ctx 0))))))

  fhirpathParser$StringLiteralContext
  (-compile [ctx]
    (->LiteralExpression
      (str/trim (.getText (.getSymbol (.STRING ctx))) "'")))

  fhirpathParser$NumberLiteralContext
  (-compile [ctx]
    (->LiteralExpression
      (let [text (.getText (.getSymbol (.NUMBER ctx)))]
        (try
          (Long/parseLong text)
          (catch Exception _
            (BigDecimal. text))))))

  fhirpathParser$DateTimeLiteralContext
  (-compile [ctx]
    (->LiteralExpression
      (let [text (subs (.getText (.getSymbol (.DATETIME ctx))) 1)]
        (try
          (type/->DateTime text)
          (catch Exception _
            (BigDecimal. text))))))

  fhirpathParser$MemberInvocationContext
  (-compile [ctx]
    (let [[first-char :as identifier] (-compile (.identifier ctx))]
      (if (Character/isUpperCase ^char first-char)
        (if (= "Resource" identifier)
          (->StartExpression)
          (->TypedStartExpression (keyword "fhir" identifier)))
        (->GetChildrenExpression (keyword identifier)))))
  (-compile-as-type-specifier [ctx]
    (let [type (-compile (.identifier ctx))]
      (if (fhir-spec/type-exists? type)
        (keyword "fhir" type)
        (throw-anom ::anom/incorrect (format "unknown FHIR type `%s`" type)))))

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
        (throw-anom ::anom/incorrect (format "unknown FHIR type `%s`" type)))))

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
        s (ANTLRInputStream. r)
        l (fhirpathLexer. s)
        t (CommonTokenStream. l)
        p (fhirpathParser. t)]
    (try
      (-compile (.expression p))
      ;(.toStringTree (.expression p) p)
      (catch Exception e
        (let [data (ex-data e)]
          (if (::anom/category data)
            (update data ::anom/message str (format " in expression `%s`" expr))
            (throw e)))))))

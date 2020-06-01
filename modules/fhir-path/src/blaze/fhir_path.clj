(ns blaze.fhir-path
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.fhir.spec :as fhir-spec]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str]
    [taoensso.timbre :as log])
  (:import
    [clojure.lang IDeref]
    [java.io StringReader Writer]
    [org.antlr.v4.runtime ANTLRInputStream CommonTokenStream]
    [org.antlr.v4.runtime.tree TerminalNode]
    [org.cqframework.cql.gen
     fhirpathLexer fhirpathParser
     fhirpathParser$TermExpressionContext
     fhirpathParser$InvocationExpressionContext
     fhirpathParser$IndexerExpressionContext
     fhirpathParser$TypeExpressionContext
     fhirpathParser$UnionExpressionContext
     fhirpathParser$EqualityExpressionContext
     fhirpathParser$AndExpressionContext
     fhirpathParser$InvocationTermContext
     fhirpathParser$LiteralTermContext
     fhirpathParser$ParenthesizedTermContext
     fhirpathParser$BooleanLiteralContext
     fhirpathParser$StringLiteralContext
     fhirpathParser$NumberLiteralContext
     fhirpathParser$MemberInvocationContext
     fhirpathParser$FunctionInvocationContext
     fhirpathParser$FunctionContext
     fhirpathParser$ParamListContext
     fhirpathParser$TypeSpecifierContext
     fhirpathParser$QualifiedIdentifierContext
     fhirpathParser$IdentifierContext])
  (:refer-clojure :exclude [eval compile resolve true? false?]))


(set! *warn-on-reflection* true)


(defprotocol Expression
  (-eval [_ context coll]))


(defprotocol Resolver
  (-resolve [_ uri]
    "Resolves `uri` into a resource."))


(defn eval
  "Evaluates the FHIRPath expression on `resource` with help of `resolver`.

  Returns either a collection of FHIR data or an anomaly in case of errors. The
  type of the FHIR data can be determined by calling `clojure.core/type` on it."
  {:arglists '([resolver expr resource])}
  [resolver expr {type :resourceType :as resource}]
  (try
    (-eval expr {:resolver resolver}
           [(with-meta resource {:type (keyword "fhir" type)})])
    (catch Exception e
      (ex-data e))))


;; allow metadata in primitives
(defrecord PrimitiveWrapper [x]
  IDeref
  (deref [_] x))


(defmethod print-method PrimitiveWrapper [^PrimitiveWrapper pw ^Writer w]
  (.write w (pr-str (.x pw))))


(defn- with-spec [spec x]
  (let [x (if (fhir-spec/primitive? spec) (->PrimitiveWrapper x) x)]
    (with-meta x {:type spec})))


(def ^:private typed-true
  (with-spec :fhir/boolean true))


(def ^:private typed-false
  (with-spec :fhir/boolean false))


(defn- true? [x]
  (and (= :fhir/boolean (type x)) (clojure.core/true? @x)))


(defn- false? [x]
  (and (= :fhir/boolean (type x)) (clojure.core/false? @x)))


(defrecord StartExpression []
  Expression
  (-eval [_ _ coll]
    coll))


(defrecord TypedStartExpression [spec]
  Expression
  (-eval [_ _ coll]
    (filterv #(= spec (type %)) coll)))


(defrecord GetChildrenExpression [key]
  Expression
  (-eval [_ _ coll]
    (reduce
      (fn [res x]
        (if-let [child-spec (get (fhir-spec/child-specs (type x)) key)]
          (cond
            (fhir-spec/choice? child-spec)
            (reduce
              (fn [res [key spec]]
                (if-some [val (get x key)]
                  (reduced (conj res (with-spec spec val)))
                  res))
              res
              (fhir-spec/choices child-spec))

            (= :many (fhir-spec/cardinality child-spec))
            (into res (map (partial with-spec (fhir-spec/type-spec child-spec))) (get x key))

            :else
            (if-some [val (get x key)]
              (conj res (with-spec child-spec val))
              res))
          (throw-anom
            ::anom/incorrect
            (format "unknown FHIR data element `%s` in type `%s`" (name key) (name (type x))))))
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


(defrecord IsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context coll]
    (let [coll (-eval expression context coll)]
      (cond
        (= 1 (count coll))
        [(if (= type-specifier (type (first coll))) typed-true typed-false)]

        (empty? coll)
        []

        :else
        (throw-anom ::anom/incorrect (format "is type specifier with more than one item at the left side `%s`" (pr-str coll)))))))


(defrecord AsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context x]
    (filterv #(= type-specifier (type %)) (-eval expression context x))))


(defrecord UnionExpression [expressions]
  Expression
  (-eval [_ context x]
    (into #{} (mapcat #(-eval % context x)) expressions)))


(defrecord EqualExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (-eval left-expr context coll)
          right (-eval right-expr context coll)]
      (if (and (empty? left) (empty? right))
        []
        ;; TODO: take all rules into consideration
        [(if (clojure.core/true? (= left right))
           typed-true
           typed-false)]))))


(defrecord NotEqualExpression [left-expr right-expr]
  Expression
  (-eval [_ context coll]
    (let [left (-eval left-expr context coll)
          right (-eval right-expr context coll)]
      (if (and (empty? left) (empty? right))
        []
        ;; TODO: take all rules into consideration
        [(if (clojure.core/true? (not= left right))
           typed-true
           typed-false)]))))


;; See: http://hl7.org/fhirpath/index.html#conversion
(defn- convertible? [type item]
  (if (= (clojure.core/type item) type)
    true
    (case [(clojure.core/type item) type]
      ([:fhir/integer :fhir/decimal]
       [:fhir/integer :fhir/Quantity]
       [:fhir/decimal :fhir/Quantity]
       [:fhir/date :fhir/dateTime])
      true)))


;; See: http://hl7.org/fhirpath/index.html#conversion
(defn- convert [type item]
  (if (= (clojure.core/type item) type)
    item
    (case [(clojure.core/type item) type]
      [:fhir/integer :fhir/decimal]
      (with-spec :fhir/decimal (BigDecimal/valueOf (long @item)))

      [:fhir/integer :fhir/Quantity]
      ;; TODO: is that right?
      (with-spec :fhir/Quantity (str @item))

      [:fhir/decimal :fhir/Quantity]
      ;; TODO: is that right?
      (with-spec :fhir/Quantity (str @item))

      [:fhir/date :fhir/dateTime]
      (with-spec :fhir/dateTime @item))))


;; See: http://hl7.org/fhirpath/index.html#singleton-evaluation-of-collections
(defn- singleton [type coll]
  (cond
    (and (= 1 (count coll)) (convertible? type (first coll)))
    (convert type (first coll))

    (and (= 1 (count coll)) (= :fhir/boolean type))
    typed-true

    (empty? coll)
    []

    :else
    (throw-anom ::anom/incorrect (format "unable to evaluate `%s` as singleton" (pr-str coll)))))


;; See: http://hl7.org/fhirpath/index.html#and
(defrecord AndExpression [expr-a expr-b]
  Expression
  (-eval [_ context coll]
    (let [a (singleton :fhir/boolean (-eval expr-a context coll))]
      (if (false? a)
        [typed-false]

        (let [b (singleton :fhir/boolean (-eval expr-b context coll))]
          (cond
            (false? b) [typed-false]
            (and (true? a) (true? b)) [typed-true]
            :else []))))))


(defrecord AsFunctionExpression [type-specifier]
  Expression
  (-eval [_ _ x]
    (filterv #(= type-specifier (type %)) x)))


;; See: http://hl7.org/fhirpath/index.html#wherecriteria-expression-collection
(defrecord WhereFunctionExpression [criteria]
  Expression
  (-eval [_ context coll]
    (filterv
      (fn [item]
        (let [res (-eval criteria context [item])]
          (cond
            (= 1 (count res))
            (if (= :fhir/boolean (type (first res)))
              (clojure.core/true? @(first res))
              (throw-anom ::anom/incorrect (format "non-boolean result `%s` of type `%s` while evaluating where function criteria" (pr-str (first res)) (type (first res)))))

            (empty? res)
            false

            :else
            (throw-anom ::anom/incorrect "multiple result items `%s` while evaluating where function criteria" (pr-str res)))))
      coll)))


(defrecord ExistsFunctionExpression []
  Expression
  (-eval [_ _ coll]
    [(if (empty? coll) typed-false typed-true)]))


(defrecord ExistsWithCriteriaFunctionExpression [criteria]
  Expression
  (-eval [_ _ _]
    (throw-anom ::anom/unsupported "unsupported `exists` function")))


(defmulti resolve (fn [_ item] (type item)))


(defn- resolve* [resolver uri]
  (when-let [{type :resourceType :as resource} (-resolve resolver uri)]
    [(with-meta resource {:type (keyword "fhir" type)})]))


(defmethod resolve :fhir/string [{:keys [resolver]} uri]
  (resolve* resolver uri))


(defmethod resolve :fhir/Reference [{:keys [resolver]} {:keys [reference]}]
  (resolve* resolver reference))


(defmethod resolve :default [_ item]
  (log/debug (format "Skip resolving %s `%s`." (name (type item)) (pr-str item))))


(defrecord ResolveFunctionExpression []
  Expression
  (-eval [_ context coll]
    (mapcat (partial resolve context) coll)))


(defrecord LiteralExpression [literal]
  Expression
  (-eval [_ _ _]
    [literal]))


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

  fhirpathParser$BooleanLiteralContext
  (-compile [ctx]
    (->LiteralExpression
      (with-spec :fhir/boolean (Boolean/valueOf (.getText (.getSymbol ^TerminalNode (.getChild ctx 0)))))))

  fhirpathParser$StringLiteralContext
  (-compile [ctx]
    (->LiteralExpression
      (with-spec :fhir/string (str/trim (.getText (.getSymbol (.STRING ctx))) "'"))))

  fhirpathParser$NumberLiteralContext
  (-compile [ctx]
    (->LiteralExpression
      (let [text (.getText (.getSymbol (.NUMBER ctx)))]
        (try
          (with-spec :fhir/integer (Long/parseLong text))
          (catch Exception _
            (with-spec :fhir/decimal (BigDecimal. text)))))))

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


(defn compile [expr]
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

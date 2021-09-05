(ns blaze.fhir-path
  (:refer-clojure :exclude [eval compile resolve])
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.anomaly-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as str]
    [taoensso.timbre :as log])
  (:import
    [clojure.lang Counted PersistentVector]
    [java.io StringReader]
    [org.antlr.v4.runtime CharStreams CommonTokenStream]
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
     fhirpathParser$DateLiteralContext
     fhirpathParser$DateTimeLiteralContext
     fhirpathParser$MemberInvocationContext
     fhirpathParser$FunctionInvocationContext
     fhirpathParser$FunctionContext
     fhirpathParser$ParamListContext
     fhirpathParser$TypeSpecifierContext
     fhirpathParser$QualifiedIdentifierContext
     fhirpathParser$IdentifierContext]))


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


;; See: http://hl7.org/fhirpath/index.html#conversion
(defn- convertible? [type item]
  (if (identical? type (fhir-spec/fhir-type item))
    true
    (case [(fhir-spec/fhir-type item) type]
      ([:fhir/integer :fhir/decimal]
       [:fhir/date :fhir/dateTime])
      true
      false)))


;; See: http://hl7.org/fhirpath/index.html#conversion
(defn- convert [type item]
  (if (identical? type (fhir-spec/fhir-type item))
    item
    (case [(fhir-spec/fhir-type item) type]
      [:fhir/integer :fhir/decimal]
      (BigDecimal/valueOf (long item))

      [:fhir/date :fhir/dateTime]
      (fhir-spec/to-date-time item))))


;; See: http://hl7.org/fhirpath/index.html#singleton-evaluation-of-collections
(defn- singleton-evaluation-msg [coll]
  (format "unable to evaluate `%s` as singleton" (pr-str coll)))


(defn- singleton [type coll]
  (case (.count ^Counted coll)
    1 (let [first (nth coll 0)]
        (cond
          (convertible? type first) (convert type first)
          (identical? :fhir/boolean type) true
          :else (throw-anom (ba/incorrect (singleton-evaluation-msg coll)))))

    0 []

    (throw-anom (ba/incorrect (singleton-evaluation-msg coll)))))


(defrecord StartExpression []
  Expression
  (-eval [_ _ coll]
    coll))


(defrecord TypedStartExpression [spec]
  Expression
  (-eval [_ _ coll]
    (filterv #(identical? spec (fhir-spec/fhir-type %)) coll)))


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
    (let [coll (-eval expression context coll)
          idx (singleton :fhir/integer (-eval index context coll))]
      [(nth coll idx [])])))


(defrecord PlusExpression [left-expr right-expr]
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


(defrecord IsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context coll]
    (let [coll (-eval expression context coll)]
      (case (.count ^Counted coll)
        0 []

        1 [(identical? type-specifier (fhir-spec/fhir-type (nth coll 0)))]

        (throw-anom (ba/incorrect (is-type-specifier-msg coll)))))))


(defn- as-type-specifier-msg [coll]
  (format "as type specifier with more than one item at the left side `%s`"
          (pr-str coll)))


(defrecord AsTypeExpression [expression type-specifier]
  Expression
  (-eval [_ context coll]
    (let [coll (-eval expression context coll)]
      (case (.count ^Counted coll)
        0 []

        1 (if (identical? type-specifier (fhir-spec/fhir-type (nth coll 0)))
            coll
            [])

        (throw-anom (ba/incorrect (as-type-specifier-msg coll)))))))


(defrecord UnionExpression [e1 e2]
  Expression
  (-eval [_ context coll]
    (let [^Counted c1 (-eval e1 context coll)
          ^Counted c2 (-eval e2 context coll)]
      (case (.count c1)
        0 (case (.count c2)
            (0 1) c2
            (vec (set c2)))
        1 (case (.count c2)
            0 c1
            1 (if (= c1 c2) c1 (conj c1 (nth c2 0)))
            (vec (conj (set c2) (nth c1 0))))
        (vec (reduce conj (set c1) c2))))))


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
  (-eval [_ _ coll]
    (case (.count ^Counted coll)
      0 []

      1 (if (identical? type-specifier (fhir-spec/fhir-type (nth coll 0)))
          coll
          [])

      (throw-anom (ba/incorrect (as-type-specifier-msg coll))))))


;; See: http://hl7.org/fhirpath/#wherecriteria-expression-collection
(defn- non-boolean-result-msg [x]
  (format "non-boolean result `%s` of type `%s` while evaluating where function criteria"
          (pr-str x) (fhir-spec/fhir-type x)))


(defn- multiple-result-msg [x]
  (format "multiple result items `%s` while evaluating where function criteria"
          (pr-str x)))


(defrecord WhereFunctionExpression [criteria]
  Expression
  (-eval [_ context coll]
    (filterv
      (fn [item]
        (let [coll (-eval criteria context [item])]
          (case (.count ^Counted coll)
            0 false

            1 (let [first (nth coll 0)]
                (if (identical? :system/boolean (system/type first))
                  first
                  (throw-anom (ba/incorrect (non-boolean-result-msg first)))))

            (throw-anom (ba/incorrect (multiple-result-msg coll))))))
      coll)))


(defrecord OfTypeFunctionExpression [type-specifier]
  Expression
  (-eval [_ _ coll]
    (filterv #(identical? type-specifier (fhir-spec/fhir-type %)) coll)))


(defrecord ExistsFunctionExpression []
  Expression
  (-eval [_ _ coll]
    [(if (empty? coll) false true)]))


(defrecord ExistsWithCriteriaFunctionExpression [criteria]
  Expression
  (-eval [_ _ _]
    (throw-anom (ba/unsupported "unsupported `exists` function"))))


(defmulti resolve (fn [_ item] (fhir-spec/fhir-type item)))


(defn- resolve* [resolver uri]
  (when-let [resource (-resolve resolver uri)]
    [resource]))


(defmethod resolve :fhir/string [{:keys [resolver]} uri]
  (resolve* resolver uri))


(defmethod resolve :fhir/Reference [{:keys [resolver]} {:keys [reference]}]
  (resolve* resolver reference))


(defmethod resolve :default [_ item]
  (log/debug (format "Skip resolving %s `%s`." (name (fhir-spec/fhir-type item))
                     (pr-str item))))


(defrecord ResolveFunctionExpression []
  Expression
  (-eval [_ context coll]
    (reduce #(reduce conj %1 (resolve context %2)) [] coll)))


(defprotocol FPCompiler
  (-compile [ctx])
  (-compile-as-type-specifier [ctx]))


(defmulti function-expression (fn [name _] name))


(defmethod function-expression "as"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [type-specifier-ctx (some-> paramsCtx (.expression 0))]
    (->AsFunctionExpression (-compile-as-type-specifier type-specifier-ctx))
    (throw-anom (ba/incorrect "missing type specifier in `as` function"))))


(defmethod function-expression "where"
  [_ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [criteria-ctx (some-> paramsCtx (.expression 0))]
    (->WhereFunctionExpression (-compile criteria-ctx))
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


;; See: https://hl7.org/fhir/fhirpath.html#functions
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
    [(str/trim (.getText (.getSymbol (.STRING ctx))) "'")])

  fhirpathParser$NumberLiteralContext
  (-compile [ctx]
    (let [text (.getText (.getSymbol (.NUMBER ctx)))]
      (try
        [(Integer/parseInt text)]
        (catch Exception _
          [(BigDecimal. text)]))))

  fhirpathParser$DateLiteralContext
  (-compile [ctx]
    (let [text (subs (.getText (.getSymbol (.DATE ctx))) 1)]
      [(type/->Date text)]))

  fhirpathParser$DateTimeLiteralContext
  (-compile [ctx]
    (let [text (subs (.getText (.getSymbol (.DATETIME ctx))) 1)
          text (if (str/ends-with? text "T") (subs text 0 (dec (count text))) text)]
      [(type/->DateTime text)]))

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
    (-> (ba/try-anomaly (-compile (.expression p)))
        (ba/exceptionally
          #(-> (update % ::anom/message str (format " in expression `%s`" expr))
               (assoc :expression expr))))))

(ns blaze.fhir-path
  (:refer-clojure :exclude [eval compile resolve])
  (:require
    [blaze.anomaly :as ba :refer [throw-anom when-ok]]
    [blaze.anomaly-spec]
    [blaze.fhir-path.protocols :as p]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as cuerdas]
    [taoensso.timbre :as log])
  (:import
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
     fhirpathParser$IdentifierContext]
    [java.util ArrayList]
    [clojure.lang IFn]))


(set! *warn-on-reflection* true)


(defn eval
  "Evaluates the FHIRPath expression on `value`.

  Returns either a collection of FHIR data or an anomaly in case of errors. The
  type of the FHIR data can be determined by calling `blaze.fhir.spec/fhir-type`
  on it."
  [expr coll]
  (-> (transduce expr conj [] coll)
      (ba/exceptionally #(assoc % :expression (meta expr) :coll coll))))


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


(defn- pr-coll [coll]
  (pr-str (into [] coll)))


(defn- expected-single-item-msg [item-1 item-2]
  (format "expected a single item but got at least two: `%s`, `%s`"
          (pr-str item-1) (pr-str item-2)))


(defn- ensure-single-item
  "A transducer that returns an anomaly if the input collection has more than
  one item."
  [rf]
  (let [first (volatile! nil)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (if (nil? @first)
         (do (vreset! first input) (rf result input))
         (reduced (ba/incorrect (expected-single-item-msg @first input)
                                :item-1 @first
                                :item-2 input)))))))


;; See: http://hl7.org/fhirpath/index.html#singleton-evaluation-of-collections
(defn- singleton-evaluation-msg [coll]
  (format "unable to evaluate `%s` as singleton" (pr-coll coll)))


(defn- singleton [type coll]
  (case (count coll)
    1 (let [first (nth coll 0)]
        (cond
          (convertible? type first) (convert type first)
          (identical? :fhir/boolean type) true
          :else (ba/incorrect (singleton-evaluation-msg coll))))

    0 []

    (ba/incorrect (singleton-evaluation-msg coll))))


(defn- get-children-xform [key]
  (mapcat
    #(let [val (key %)]
       (cond
         (sequential? val) val
         (some? val) [val]))))


(defn- resolve-is-type [type]
  (comp
    (keep :reference)
    ensure-single-item
    (map #(str/starts-with? % type))))


;; 5.1. Existence

;; 5.1.2. exists([criteria : expression]) : Boolean
(def ^:private exists-expr
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf (if (empty? result) [false] result)))
      ([_ _]
       (reduced [true])))))


;; 5.2. Filtering and projection

;; 5.2.1. where(criteria : expression) : collection
(defn- non-boolean-result-msg [x]
  (format "non-boolean result `%s` of type `%s` while evaluating where function criteria"
          (pr-str x) (fhir-spec/fhir-type x)))

(defn- halting-filter [pred]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (let [r (pred input)]
         (cond
           (ba/anomaly? r) r
           r (rf result input)
           :else result))))))


(defn- where [criteria]
  (halting-filter
    #(transduce
       criteria
       (fn
         ([ret] ret)
         ([ret item]
          (if (boolean? ret)
            (reduced (ba/incorrect "more than one item"))
            (if (boolean? item)
              item
              (reduced (ba/incorrect (non-boolean-result-msg item)))))))
       nil
       [%])))


;; 5.2.4. ofType(type : type specifier) : collection

(defn- of-type? [type]
  #(identical? type (fhir-spec/fhir-type %)))

(defn- of-type [type]
  (filter (of-type? type)))



;; 5.4. Combining

;; 5.4.1. union(other : collection)
(defn- union-expr
  "Returns a transducer that will run the both transducers `e1` and `e2` on the
  complete input collection and return the union of their outputs."
  [e1 e2]
  (fn [rf]
    (let [coll (ArrayList.)]
      (fn
        ([] (rf))
        ([result]
         (let [c1 (transduce e1 conj [] coll)
               c2 (transduce e2 conj [] coll)]
           (.clear coll)
           (case (count c1)
             0 (case (count c2)
                 0 result
                 1 (reduce rf result c2)
                 (reduce rf result (set c2)))
             1 (case (count c2)
                 0 (reduce rf result c1)
                 1 (if (= c1 c2)
                     (reduce rf result c1)
                     (reduce rf (reduce rf result c1) c2))
                 (reduce rf result (conj (set c2) (nth c1 0))))
             (reduce rf result (reduce conj (set c1) c2)))))
        ([result input]
         (.add coll input)
         result)))))



;; 6. Operations

;; 6.1. Equality

;; 6.1.1. = (Equals)
(defn- equal-expr [l r]
  (fn [rf]
    (let [coll (ArrayList.)]
      (fn
        ([] (rf))
        ([result]
         (let [ls (transduce l conj coll)
               rs (transduce r conj coll)]
           (.clear coll)
           (cond
             (or (empty? ls) (empty? rs)) result
             (not= (count ls) (count rs)) (rf result false)
             :else
             (loop [[l & ls] ls [r & rs] rs]
               (if (system/equals (type/value l) (type/value r))
                 (if (empty? ls)
                   (rf result true)
                   (recur ls rs))
                 (rf result false))))))
        ([result input]
         (.add coll input)
         result)))))


;; 6.1.3. != (Not Equals)
(defn- not-equal-expr [l r]
  (fn [rf]
    (let [coll (ArrayList.)]
      (fn
        ([] (rf))
        ([result]
         (let [ls (transduce l conj coll)
               rs (transduce r conj coll)]
           (.clear coll)
           (cond
             (or (empty? ls) (empty? rs)) result
             (not= (count ls) (count rs)) (rf result false)
             :else
             (loop [[l & ls] ls [r & rs] rs]
               (if (system/equals (type/value l) (type/value r))
                 (if (empty? ls)
                   (rf result false)
                   (recur ls rs))
                 (rf result true))))))
        ([result input]
         (.add coll input)
         result)))))


;; 6.3. Types

;; 6.3.1. is type specifier
;; 6.3.2. is(type : type specifier)

(defn- is-type [type]
  (comp ensure-single-item (map (of-type? type))))


;; 6.3.3. as type specifier
;; 6.3.4. as(type : type specifier)

(defn- as-type [type]
  (comp ensure-single-item (of-type type)))



;; 6.5. Boolean logic

;; 6.5.1. and
(defn- and-expr [l r]
  (fn [rf]
    (let [coll (ArrayList.)]
      (fn
        ([] (rf))
        ([result]
         (let [c (vec coll)]
           (.clear coll)
           (when-ok [l (singleton :fhir/boolean (transduce l conj [] c))]
             (if (false? l)
               (rf result false)

               (when-ok [r (singleton :fhir/boolean (transduce r conj [] c))]
                 (cond
                   (false? r) (rf result false)
                   (and (true? l) (true? r)) (rf result true)
                   :else result))))))
        ([result input]
         (.add coll input)
         result)))))


;; 6.5.3. not() : Boolean

;; 6.6. Math

;; 6.6.3. + (addition)
(defn- plus-expr [l r]
  (fn [rf]
    (let [coll (ArrayList.)]
      (fn
        ([] (rf))
        ([result]
         (let [c (vec coll)]
           (.clear coll)
           (when-ok [l (singleton :fhir/string (transduce l conj [] c))
                     r (singleton :fhir/string (transduce r conj [] c))]
             (cond
               (empty? l) (rf result r)
               (empty? r) (rf result l)
               :else (rf result (str l r))))))
        ([result input]
         (.add coll input)
         result)))))


(defn- skip-resolving-msg [item]
  (format "Skip resolving %s `%s`." (name (fhir-spec/fhir-type item)) (pr-str item)))


(defn- resolve* [{:keys [resolver]} uri]
  (p/-resolve resolver uri))


(defn- resolve [context item]
  (case (fhir-spec/fhir-type item)
    :fhir/string
    (some->> item (resolve* context))
    :fhir/Reference
    (some->> item :reference (resolve* context))
    (log/debug (skip-resolving-msg item))))


(defprotocol FPCompiler
  (-compile [ctx context])
  (-compile-as-type-specifier [ctx context]))


(defmulti function-expression (fn [_ name _] name))


(defmethod function-expression "as"
  [context _ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [type-specifier-ctx (some-> paramsCtx (.expression 0))]
    (when-ok [type (-compile-as-type-specifier type-specifier-ctx context)]
      (as-type type))
    (ba/incorrect "missing type specifier in `as` function")))


(defmethod function-expression "where"
  [context _ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [criteria-ctx (some-> paramsCtx (.expression 0))]
    (where (-compile criteria-ctx context))
    (ba/incorrect "missing criteria in `where` function")))


(defmethod function-expression "ofType"
  [context _ ^fhirpathParser$ParamListContext paramsCtx]
  (if-let [type-specifier-ctx (some-> paramsCtx (.expression 0))]
    (when-ok [type (-compile-as-type-specifier type-specifier-ctx context)]
      (of-type type))
    (ba/incorrect "missing type specifier in `as` function")))


(defmethod function-expression "exists"
  [_ _ ^fhirpathParser$ParamListContext paramsCtx]
  (if (some-> paramsCtx (.expression 0))
    (ba/unsupported "unsupported exists() function with criteria")
    exists-expr))


;; See: https://hl7.org/fhir/fhirpath.html#functions
(defmethod function-expression "resolve"
  [context _ _]
  (keep (partial resolve context)))


(defmethod function-expression :default
  [name paramsCtx]
  (ba/incorrect
    (format "unknown function `%s`" name)
    :name name
    :paramsCtx paramsCtx))


(deftype FunctionExpression [name xf]
  IFn
  (invoke [_ rf] (xf rf)))


(defn- constant-expr
  "Returns a transducer returning always `coll`."
  [coll]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([_ _]
       coll))))


(deftype NumberLiteral [xf value]
  IFn
  (invoke [_ rf] (xf rf)))


(defn- parse-number [^String text]
  (try
    (Integer/parseInt text)
    (catch Exception _
      (BigDecimal. text))))


(defn- number-literal? [expr]
  (-> expr class (identical? NumberLiteral)))


(defn- compile-binary-expr [context l r _name expr-fn]
  (when-ok [l (-compile l context)
            r (-compile r context)]
    (expr-fn l r)
    #_(with-meta (expr-fn l r) {:name name :expressions [(meta l) (meta r)]})))


(defn- resolve-function? [expr]
  (and (identical? FunctionExpression (class expr))
       (= "resolve" (.name ^FunctionExpression expr))))


(extend-protocol FPCompiler
  fhirpathParser$TermExpressionContext
  (-compile [ctx context]
    (-compile (.term ctx) context))
  (-compile-as-type-specifier [ctx context]
    (-compile-as-type-specifier (.term ctx) context))

  fhirpathParser$InvocationExpressionContext
  (-compile [ctx context]
    (when-ok [expression (-compile (.expression ctx) context)
              invocation (-compile (.invocation ctx) context)]
      (comp expression invocation)
      #_(with-meta (comp expression invocation)
                 {:name "InvocationExpression"
                  :expression (meta expression)
                  :invocation (meta invocation)})))

  fhirpathParser$IndexerExpressionContext
  (-compile [ctx context]
    (when-ok [expr (-compile (.expression ctx 0) context)
              idx (-compile (.expression ctx 1) context)]
      (if (number-literal? idx)
        (let [idx (.value ^NumberLiteral idx)]
          (comp expr (drop idx) (take 1))
          #_(with-meta (comp expr (drop idx) (take 1))
                     {:name "IndexerExpression"
                      :expression (meta expr)
                      :index idx}))
        (ba/unsupported (str "unsupported non constant index expression: " (pr-str (meta idx)))))))

  fhirpathParser$AdditiveExpressionContext
  (-compile [ctx context]
    (when-ok [e1 (-compile (.expression ctx 0) context)
              e2 (-compile (.expression ctx 1) context)]
      (case (.getText (.getSymbol ^TerminalNode (.getChild ctx 1)))
        "+"
        (plus-expr e1 e2)
        #_(with-meta (plus-expr e1 e2)
                   {:name "PlusExpression"
                    :expressions [(meta e1) (meta e2)]}))))

  fhirpathParser$TypeExpressionContext
  (-compile [ctx context]
    (let [expr (-compile (.expression ctx) context)
          type (-compile (.typeSpecifier ctx) context)]
      (case (.getText (.getSymbol ^TerminalNode (.getChild ctx 1)))
        "is"
        (if (resolve-function? expr)
          (resolve-is-type (str (name type) "/"))
          #_(with-meta (resolve-is-type (str (name type) "/"))
                     {:name "IsTypeExpression"
                      :expression (meta expr)
                      :type type})
          (comp expr (is-type type))
          #_(with-meta (comp expr (is-type type))
                     {:name "IsTypeExpression"
                      :expression (meta expr)
                      :type type}))
        "as"
        (comp expr (as-type type))
        #_(with-meta (comp expr (as-type type))
                   {:name "AsTypeExpression"
                    :expression (meta expr)
                    :type type}))))

  fhirpathParser$UnionExpressionContext
  (-compile [ctx context]
    (compile-binary-expr context (.expression ctx 0) (.expression ctx 1)
                         "UnionExpression" union-expr))

  fhirpathParser$EqualityExpressionContext
  (-compile [ctx context]
    (case (.getText (.getSymbol ^TerminalNode (.getChild ctx 1)))
      "="
      (compile-binary-expr context (.expression ctx 0) (.expression ctx 1)
                           "EqualExpression" equal-expr)
      "!="
      (compile-binary-expr context (.expression ctx 0) (.expression ctx 1)
                           "NotEqualExpression" not-equal-expr)))

  fhirpathParser$AndExpressionContext
  (-compile [ctx context]
    (compile-binary-expr context (.expression ctx 0) (.expression ctx 1)
                         "AndExpression" and-expr))

  fhirpathParser$InvocationTermContext
  (-compile [ctx context]
    (-compile (.invocation ctx) context))
  (-compile-as-type-specifier [ctx context]
    (-compile-as-type-specifier (.invocation ctx) context))

  fhirpathParser$LiteralTermContext
  (-compile [ctx context]
    (-compile (.literal ctx) context))

  fhirpathParser$ParenthesizedTermContext
  (-compile [ctx context]
    (-compile (.expression ctx) context))

  fhirpathParser$NullLiteralContext
  (-compile [_ _]
    (constant-expr nil)
    #_(with-meta (constant-expr nil) {:name "NullLiteral"}))

  fhirpathParser$BooleanLiteralContext
  (-compile [ctx _]
    (let [val (Boolean/valueOf (.getText (.getSymbol ^TerminalNode (.getChild ctx 0))))]
      (constant-expr [val])
      #_(with-meta (constant-expr [val]) {:name "BooleanLiteral" :value val})))

  fhirpathParser$StringLiteralContext
  (-compile [ctx _]
    (let [val (cuerdas/trim (.getText (.getSymbol (.STRING ctx))) "'")]
      (constant-expr [val])
      #_(with-meta (constant-expr [val]) {:name "StringLiteral" :value val})))

  fhirpathParser$NumberLiteralContext
  (-compile [ctx _]
    (let [val (parse-number (.getText (.getSymbol (.NUMBER ctx))))]
      (->NumberLiteral (constant-expr [val]) val)
      #_(with-meta (constant-expr [val]) {:name "NumberLiteral" :value val})))

  fhirpathParser$DateLiteralContext
  (-compile [ctx _]
    (let [val (type/->Date (subs (.getText (.getSymbol (.DATE ctx))) 1))]
      (constant-expr [val])
      #_(with-meta (constant-expr [val]) {:name "DateLiteral" :value val})))

  fhirpathParser$DateTimeLiteralContext
  (-compile [ctx _]
    (let [text (subs (.getText (.getSymbol (.DATETIME ctx))) 1)
          text (if (str/ends-with? text "T") (subs text 0 (dec (count text))) text)
          val (type/->DateTime text)]
      (constant-expr [val])
      #_(with-meta (constant-expr [val]) {:name "DateTimeLiteral" :value val})))

  fhirpathParser$MemberInvocationContext
  (-compile [ctx context]
    (let [[first-char :as identifier] (-compile (.identifier ctx) context)]
      (if (Character/isUpperCase ^char first-char)
        (if (= "Resource" identifier)
          identity
          (of-type (keyword "fhir" identifier)))
        (get-children-xform (keyword identifier)))
      #_(with-meta
        (if (Character/isUpperCase ^char first-char)
          (if (= "Resource" identifier)
            identity
            (of-type (keyword "fhir" identifier)))
          (get-children-xform (keyword identifier)))
        {:name "MemberInvocation"
         :identifier identifier})))
  (-compile-as-type-specifier [ctx context]
    (let [type (-compile (.identifier ctx) context)]
      (if (fhir-spec/type-exists? type)
        (keyword "fhir" type)
        (throw-anom (ba/incorrect (format "unknown FHIR type `%s`" type))))))

  fhirpathParser$FunctionInvocationContext
  (-compile [ctx context]
    (-compile (.function ctx) context))

  fhirpathParser$FunctionContext
  (-compile [ctx context]
    (let [name (-compile (.identifier ctx) context)]
      (when-ok [xf (function-expression context name (.paramList ctx))]
        (->FunctionExpression name xf))
      #_(with-meta (function-expression context name (.paramList ctx))
                 {:name "FunctionExpression"
                  :function-name name})))

  fhirpathParser$TypeSpecifierContext
  (-compile [ctx context]
    (let [type (first (-compile (.qualifiedIdentifier ctx) context))]
      (if (fhir-spec/type-exists? type)
        (keyword "fhir" type)
        (throw-anom (ba/incorrect (format "unknown FHIR type `%s`" type))))))

  fhirpathParser$QualifiedIdentifierContext
  (-compile [ctx context]
    (mapv #(-compile % context) (.identifier ctx)))

  fhirpathParser$IdentifierContext
  (-compile [ctx _]
    (when-let [^TerminalNode node (.getChild ctx 0)]
      (.getText (.getSymbol node)))))


(defn compile
  "Compiles the FHIRPath `expr` with help of `resolver`.

  Returns either a compiled FHIRPath expression or an anomaly in case of errors."
  [resolver expr]
  (let [r (StringReader. expr)
        s (CharStreams/fromReader r)
        l (fhirpathLexer. s)
        t (CommonTokenStream. l)
        p (fhirpathParser. t)]
    (-> (ba/try-anomaly (-compile (.expression p) {:resolver resolver}))
        (ba/exceptionally
          #(-> (update % ::anom/message str (format " in expression `%s`" expr))
               (assoc :expression expr))))))


(comment
  (require '[clj-java-decompiler.core :refer [decompile]])

  (binding [*compiler-options* {:direct-linking true}]
    (decompile)))

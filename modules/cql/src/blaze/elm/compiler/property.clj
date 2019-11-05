(ns blaze.elm.compiler.property
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.datomic.util :as datomic-util]
    [blaze.datomic.value :as dv]
    [blaze.elm.compiler.protocols :refer [Expression -eval expr?]]
    [blaze.elm.spec]
    [blaze.elm.util :as elm-util]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]))


(s/fdef choice-result-type?
  :args (s/cat :expression :elm/expression))

(defn choice-result-type?
  [{result-type-specifier :resultTypeSpecifier}]
  (some-> result-type-specifier elm-util/choice-type-specifier?))


(defn- first-lower-case [s]
  (let [[^char ch & chs] s]
    (if (Character/isUpperCase ch)
      (apply str (cons (Character/toLowerCase ch) chs))
      s)))


(defn- attr-kw [type-name path]
  (let [[first-type-name & type-names] (str/split type-name #"\.")]
    (keyword
      (str/join "." (cons first-type-name (map first-lower-case type-names)))
      path)))


(s/fdef attr
  :args (s/cat :expression :elm/expression)
  :ret (s/nilable keyword?))

(defn attr
  "Returns the Datomic attribute which corresponds to the :path of the
  expression if :life/source-type or the type of :source is known."
  {:arglists '([expression])}
  [{:life/keys [source-type] :keys [source path] :as expr}]
  (cond
    source-type
    (let [[type-ns type-name] (elm-util/parse-qualified-name source-type)]
      (if (= "http://hl7.org/fhir" type-ns)
        (attr-kw type-name path)
        (throw-anom
          ::anom/unsupported
          (format
            "Unsupported source type namespace `%s` in property expression with path `%s`."
            type-ns path)
          :expression expr)))

    source
    (let [{type-specifier :resultTypeSpecifier type-name :resultTypeName} source]
      (cond
        (some-> type-specifier elm-util/tuple-type-specifier?)
        (keyword path)

        type-name
        (attr (assoc expr :life/source-type type-name))

        ;; TODO: HACK
        (= "birthDate.value" path)
        :Patient/birthDate))))


(defrecord SourceChoiceTypePropertyExpression [source type-attr]
  Expression
  (-eval [_ context resource scope]
    (let [value (-eval source context resource scope)]
      (when-let [type-attr (type-attr value)]
        (dv/read (type-attr value))))))


(s/fdef source-choice-type-expr
  :args (s/cat :source expr? :type-attr keyword?))

(defn source-choice-type-expr
  [source type-attr]
  (->SourceChoiceTypePropertyExpression source type-attr))


(defrecord ScopeChoiceTypePropertyExpression [key type-attr]
  Expression
  (-eval [_ _ _ scope]
    (let [value (get scope key)]
      (dv/read ((type-attr value) value)))))


(s/fdef scope-choice-type-expr
  :args (s/cat :scope string? :type-attr keyword?))

(defn scope-choice-type-expr [scope type-attr]
  (->ScopeChoiceTypePropertyExpression scope type-attr))


(defrecord SingleScopeChoiceTypePropertyExpression [type-attr]
  Expression
  (-eval [_ _ _ scope]
    (dv/read ((type-attr scope) scope))))


(s/fdef single-scope-choice-type-expr
  :args (s/cat :type-attr keyword?))

(defn single-scope-choice-type-expr [type-attr]
  (->SingleScopeChoiceTypePropertyExpression type-attr))


(defrecord SourcePropertyExpression [source attr]
  Expression
  (-eval [_ context resource scope]
    (dv/read (attr (-eval source context resource scope)))))


(s/fdef source-expr
  :args (s/cat :source expr? :attr keyword?))

(defn source-expr
  [source attr]
  (->SourcePropertyExpression source attr))


(defrecord ScopePropertyExpression [key attr]
  Expression
  (-eval [_ _ _ scope]
    (dv/read (attr (get scope key)))))


(s/fdef scope-expr
  :args (s/cat :scope string? :attr keyword?))

(defn scope-expr [scope attr]
  (->ScopePropertyExpression scope attr))


(defrecord SingleScopePropertyExpression [attr]
  Expression
  (-eval [_ _ _ scope]
    (dv/read (attr scope))))


(s/fdef single-scope-expr
  :args (s/cat :attr keyword?))

(defn single-scope-expr [attr]
  (->SingleScopePropertyExpression attr))


(defn- navigate [value path]
  ((keyword (datomic-util/entity-type value) path) value))


(defrecord SourceRuntimeTypePropertyExpression [source path]
  Expression
  (-eval [_ context resource scope]
    (when-some [value (-eval source context resource scope)]
      (navigate value path))))


(s/fdef source-runtime-type-expr
  :args (s/cat :source expr? :path string?))

(defn source-runtime-type-expr [source path]
  (->SourceRuntimeTypePropertyExpression source path))


(defrecord ScopeRuntimeTypePropertyExpression [key path]
  Expression
  (-eval [_ _ _ scope]
    (when-some [value (get scope key)]
      (navigate value path))))


(s/fdef scope-runtime-type-expr
  :args (s/cat :scope string? :path string?))

(defn scope-runtime-type-expr [scope path]
  (->ScopeRuntimeTypePropertyExpression scope path))


(defrecord SingleScopeRuntimeTypePropertyExpression [path]
  Expression
  (-eval [_ _ _ scope]
    (when scope
      (navigate scope path))))


(s/fdef single-scope-runtime-type-expr
  :args (s/cat :path string?))

(defn single-scope-runtime-type-expr [path]
  (->SingleScopeRuntimeTypePropertyExpression path))

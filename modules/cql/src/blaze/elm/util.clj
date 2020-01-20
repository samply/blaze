(ns blaze.elm.util
  (:require
    [blaze.elm.spec]
    [clojure.spec.alpha :as s]))


(s/fdef parse-qualified-name
  :args (s/cat :s string?))

(defn parse-qualified-name
  "Parses a string `s` like `{urn:hl7-org:elm-types:r1}String` into a tuple
  of the namespace and the name where the namespace occurs in the braces.

  Returns nil, if the string `s` isn't a valid qualified name."
  [s]
  (let [[_ ns name] (re-matches #"\{(.+)\}(.+)" s)]
    [ns name]))


(s/fdef named-type-specifier?
  :args (s/cat :type-specifier :elm/type-specifier))

(defn named-type-specifier?
  {:arglists '([type-specifier])}
  [{:keys [type]}]
  (= "NamedTypeSpecifier" type))


(s/fdef tuple-type-specifier?
  :args (s/cat :type-specifier :elm/type-specifier))

(defn tuple-type-specifier?
  {:arglists '([type-specifier])}
  [{:keys [type]}]
  (= "TupleTypeSpecifier" type))


(s/fdef choice-type-specifier?
  :args (s/cat :type-specifier :elm/type-specifier))

(defn choice-type-specifier?
  {:arglists '([type-specifier])}
  [{:keys [type]}]
  (= "ChoiceTypeSpecifier" type))


(s/fdef list-type-specifier?
  :args (s/cat :type-specifier :elm/type-specifier))

(defn list-type-specifier?
  {:arglists '([type-specifier])}
  [{:keys [type]}]
  (= "ListTypeSpecifier" type))

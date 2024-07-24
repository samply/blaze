(ns blaze.elm.util
  (:import
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(defn pascal->kebab [s]
  (.to CaseFormat/UPPER_CAMEL CaseFormat/LOWER_HYPHEN s))

(defn parse-qualified-name
  "Parses a string `s` like `{urn:hl7-org:elm-types:r1}String` into a tuple
  of the namespace and the name where the namespace occurs in the braces.

  Returns nil, if the string `s` isn't a valid qualified name."
  [s]
  (when-let [[_ ns name] (some->> s (re-matches #"\{(.+)\}(.+)"))]
    [ns name]))

(defn parse-type
  "Transforms `type-specifier` into either the type name or a vector of the
  type name for list types."
  {:arglists '([type-specifier])}
  [{:keys [type name] element-type :elementType}]
  (condp = type
    "NamedTypeSpecifier"
    (second (parse-qualified-name name))
    "ListTypeSpecifier"
    [(parse-type element-type)]))

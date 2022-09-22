(ns blaze.elm.util)


(defn parse-qualified-name
  "Parses a string `s` like `{urn:hl7-org:elm-types:r1}String` into a tuple
  of the namespace and the name where the namespace occurs in the braces.

  Returns nil, if the string `s` isn't a valid qualified name."
  [s]
  (when-let [[_ ns name] (some->> s (re-matches #"\{(.+)\}(.+)"))]
    [ns name]))


(defn parse-type
  [{:keys [type name] element-type :elementType}]
  (condp = type
    "NamedTypeSpecifier"
    (second (parse-qualified-name name))
    "ListTypeSpecifier"
    (str "List<" (parse-type element-type) ">")))

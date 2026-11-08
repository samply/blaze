(ns blaze.elm.util
  (:refer-clojure :exclude [count])
  (:require
   [blaze.coll.core :as coll])
  (:import
   [clojure.lang IReduceInit]
   [com.google.common.base CaseFormat]))

(set! *warn-on-reflection* true)

(defn count
  "Returns the number of items in `x`, which can be a list or a string.

  Unlike `clojure.core/count`, also counts reducible collections that aren't
  counted, like the results of a Retrieve, by reducing them in O(n)."
  [x]
  (if (and (instance? IReduceInit x) (not (counted? x)))
    (coll/count-reducible x)
    (clojure.core/count x)))

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

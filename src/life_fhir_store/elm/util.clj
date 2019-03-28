(ns life-fhir-store.elm.util
  (:require
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

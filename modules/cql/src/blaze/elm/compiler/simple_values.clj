(ns blaze.elm.compiler.simple-values
  "1. Simple Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:refer-clojure :exclude [parse-long str])
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.decimal :as decimal]
   [blaze.elm.util :as elm-util]
   [blaze.util :refer [str]]))

(set! *warn-on-reflection* true)

;; 1.1 Literal
;;
;; The Literal type defines a single scalar value. For example, the literal 5,
;; the boolean value true or the string "antithrombotic".
(defn- unsupported-literals-anom [value-type]
  (ba/unsupported (str value-type " literals are not supported")))

(defn- parse-int [s]
  (try
    (.longValue (Integer/valueOf ^String s))
    (catch NumberFormatException _
      (throw-anom (ba/incorrect (format "Incorrect integer literal `%s`." s))))))

(defn- parse-long [s]
  (try
    (Long/parseLong s)
    (catch NumberFormatException _
      (throw-anom (ba/incorrect (format "Incorrect long literal `%s`." s))))))

(defmethod core/compile* :elm.compiler.type/literal
  [_ {:keys [value] value-type :valueType}]
  (when value
    (let [[value-type-ns value-type-name] (elm-util/parse-qualified-name value-type)]
      (case value-type-ns
        "urn:hl7-org:elm-types:r1"
        (case value-type-name
          "Boolean" (parse-boolean value)
          ;; TODO: maybe we can even use integers here
          "Integer" (parse-int value)
          "Long" (parse-long value)
          "Decimal" (decimal/from-literal value)
          "String" value
          (throw-anom (unsupported-literals-anom value-type)))
        (throw-anom (unsupported-literals-anom value-type))))))

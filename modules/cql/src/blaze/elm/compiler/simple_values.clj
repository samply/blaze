(ns blaze.elm.compiler.simple-values
  "1. Simple Values"
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.decimal :as decimal]
    [blaze.elm.util :as elm-util]))


;; 1.1 Literal
;;
;; The Literal type defines a single scalar value. For example, the literal 5,
;; the boolean value true or the string "antithrombotic".
(defmethod core/compile* :elm.compiler.type/literal
  [_ {:keys [value] value-type :valueType}]
  (when value
    (let [[value-type-ns value-type-name] (elm-util/parse-qualified-name value-type)]
      (case value-type-ns
        "urn:hl7-org:elm-types:r1"
        (case value-type-name
          "Boolean" (Boolean/valueOf ^String value)
          ;; TODO: maybe we can even use integers here
          "Integer" (long (Integer/parseInt value))
          "Decimal" (decimal/from-literal value)
          "String" value
          (throw-anom
            (ba/unsupported (str value-type " literals are not supported"))))
        (throw-anom
          (ba/unsupported (str value-type " literals are not supported")))))))

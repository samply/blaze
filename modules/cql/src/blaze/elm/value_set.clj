(ns blaze.elm.value-set
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.ts-util :as tu]
   [blaze.elm.value-set.protocol :as p]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service :as ts]))

(defn value-set? [x]
  (satisfies? p/ValueSet x))

(defn contains-string? [value-set code]
  (p/-contains-string value-set code))

(defn contains-code? [value-set code]
  (p/-contains-code value-set code))

(defn contains-concept? [value-set concept]
  (p/-contains-concept value-set concept))

(defn expand [value-set]
  (p/-expand value-set))

(defn- system-param [system]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "system"
   :value (type/uri system)})

(defn value-set [terminology-service url]
  (let [url-param {:fhir/type :fhir.Parameters/parameter
                   :name #fhir/string "url"
                   :value (type/uri url)}
        infer-system-param {:fhir/type :fhir.Parameters/parameter
                            :name #fhir/string "inferSystem"
                            :value #fhir/boolean true}]
    (reify
      core/Expression
      (-static [_]
        true)
      (-attach-cache [expr _]
        [(fn [] [expr])])
      (-patient-count [_]
        nil)
      (-resolve-refs [expr _]
        expr)
      (-resolve-params [expr _]
        expr)
      (-optimize [expr _]
        expr)
      (-eval [this _ _ _]
        this)
      (-form [_]
        (list 'value-set url))

      p/ValueSet
      (-contains-string [_ code]
        (tu/extract-result
         (ts/value-set-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter [url-param (tu/code-param code) infer-system-param]})
         (fn [cause-msg]
           (format
            "Error while testing that the code `%s` is in ValueSet `%s`. Cause: %s"
            code url cause-msg))))
      (-contains-code [_ code]
        (tu/extract-result
         (ts/value-set-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter
            [url-param (tu/code-param (:code code)) (system-param (:system code))]})
         (fn [cause-msg]
           (format
            "Error while testing that the %s is in ValueSet `%s`. Cause: %s"
            code url cause-msg))))
      (-contains-concept [_ concept]
        (tu/extract-result
         (ts/value-set-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter [url-param (tu/codeable-concept-param concept)]})
         (fn [cause-msg]
           (format
            "Error while testing that the %s is in ValueSet `%s`. Cause: %s"
            concept url cause-msg))))
      (-expand [_]
        (tu/extract-codes
         (ts/expand-value-set
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter [url-param]})
         (fn [cause-msg]
           (format
            "Error while expanding the ValueSet `%s`. Cause: %s"
            url cause-msg)))))))

(ns blaze.elm.code-system
  (:require
   [blaze.elm.code-system.protocol :as p]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.ts-util :as tu]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service :as ts])
  (:import
   [clojure.lang ILookup]))

(set! *warn-on-reflection* true)

(defn contains-string? [code-system code]
  (p/-contains-string code-system code))

(defn contains-code? [code-system code]
  (p/-contains-code code-system code))

(defn contains-concept? [code-system concept]
  (p/-contains-concept code-system concept))

(defn code-system
  {:arglists '([terminology-service code-system-def])}
  [terminology-service {system :id :keys [version]}]
  (let [url-param {:fhir/type :fhir.Parameters/parameter
                   :name #fhir/string "url"
                   :value (type/uri system)}]
    (reify
      ILookup
      (valAt [code-system key]
        (.valAt code-system key nil))
      (valAt [_ key not-found]
        (case key
          :system system
          :version version
          not-found))

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
        (conj (some-> version list) system 'code-system))

      p/CodeSystem
      (-contains-string [_ code]
        (tu/extract-result
         (ts/code-system-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter [url-param (tu/code-param code)]})
         (fn [cause-msg]
           (format
            "Error while testing that the code `%s` is in CodeSystem `%s`. Cause: %s"
            code system cause-msg))))
      (-contains-code [_ code]
        (tu/extract-result
         (ts/code-system-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter
            [url-param (tu/code-param (:code code))]})
         (fn [cause-msg]
           (format
            "Error while testing that the %s is in CodeSystem `%s`. Cause: %s"
            code system cause-msg))))
      (-contains-concept [_ concept]
        (tu/extract-result
         (ts/code-system-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter [url-param (tu/codeable-concept-param concept)]})
         (fn [cause-msg]
           (format
            "Error while testing that the %s is in CodeSystem `%s`. Cause: %s"
            concept system cause-msg)))))))

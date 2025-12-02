(ns blaze.elm.value-set
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.value-set.protocol :as p]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service :as ts]))

(defn contains-string? [value-set code]
  (p/-contains-string value-set code))

(defn contains-code? [value-set code]
  (p/-contains-code value-set code))

(defn contains-concept? [value-set concept]
  (p/-contains-concept value-set concept))

(def ^:private result-pred
  #(when (= "result" (type/value (:name %))) %))

(defn- extract-result [response msg-fn]
  (try
    (type/value (:value (some result-pred (:parameter @response))))
    (catch Exception e
      (ba/throw-anom (ba/fault (msg-fn (ex-message (ex-cause e))))))))

(defn- code-param [code]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "code"
   :value (type/code code)})

(defn- system-param [system]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "system"
   :value (type/uri system)})

(defn- to-coding [{:keys [system code]}]
  (type/coding
   (cond-> {}
     system (assoc :system (type/uri system))
     code (assoc :code (type/code code)))))

(defn- to-codeable-concept [{:keys [codes]}]
  (type/codeable-concept {:coding (mapv to-coding codes)}))

(defn- codeable-concept-param [concept]
  {:fhir/type :fhir.Parameters/parameter
   :name #fhir/string "codeableConcept"
   :value (to-codeable-concept concept)})

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
        (extract-result
         (ts/value-set-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter [url-param (code-param code) infer-system-param]})
         (fn [cause-msg]
           (format
            "Error while testing that the code `%s` is in ValueSet `%s`. Cause: %s"
            code url cause-msg))))
      (-contains-code [_ code]
        (extract-result
         (ts/value-set-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter
            [url-param (code-param (:code code)) (system-param (:system code))]})
         (fn [cause-msg]
           (format
            "Error while testing that the %s is in ValueSet `%s`. Cause: %s"
            code url cause-msg))))
      (-contains-concept [_ concept]
        (extract-result
         (ts/value-set-validate-code
          terminology-service
           {:fhir/type :fhir/Parameters
            :parameter [url-param (codeable-concept-param concept)]})
         (fn [cause-msg]
           (format
            "Error while testing that the %s is in ValueSet `%s`. Cause: %s"
            concept url cause-msg)))))))

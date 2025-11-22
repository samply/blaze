(ns blaze.elm.value-set
  (:require
   [blaze.anomaly :as ba]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.value-set.protocol :as p]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
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

(defn- to-coding [{:keys [system code]}]
  (type/coding
   (cond-> {}
     system (assoc :system (type/uri system))
     code (assoc :code (type/code code)))))

(defn- to-codeable-concept [{:keys [codes]}]
  (type/codeable-concept {:coding (mapv to-coding codes)}))

(defn value-set [terminology-service url]
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
         (fu/parameters
          "url" (type/uri url)
          "code" (type/code code)
          "inferSystem" #fhir/boolean true))
       (fn [cause-msg]
         (format
          "Error while testing that the code `%s` is in ValueSet `%s`. Cause: %s"
          code url cause-msg))))
    (-contains-code [_ code]
      (extract-result
       (ts/value-set-validate-code
        terminology-service
         (fu/parameters
          "url" (type/uri url)
          "system" (type/code (:system code))
          "code" (type/code (:code code))))
       (fn [cause-msg]
         (format
          "Error while testing that the %s is in ValueSet `%s`. Cause: %s"
          code url cause-msg))))
    (-contains-concept [_ concept]
      (extract-result
       (ts/value-set-validate-code
        terminology-service
         (fu/parameters
          "url" (type/uri url)
          "codeableConcept" (to-codeable-concept concept)))
       (fn [cause-msg]
         (format
          "Error while testing that the %s is in ValueSet `%s`. Cause: %s"
          concept url cause-msg))))))

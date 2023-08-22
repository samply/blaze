(ns blaze.elm.compiler.parameters
  "7. Parameters

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [blaze.anomaly :as ba :refer [throw-anom]]
    [blaze.elm.compiler.core :as core]))


;; 7.1. ParameterDef
;;
;; Not needed because it's not an expression.


;; 7.2. ParameterRef
(defn- parameter-value-not-found-anom [context name]
  (ba/incorrect
    (format "Value of parameter `%s` not found." name)
    :context context))


(defrecord ParameterRef [name]
  core/Expression
  (-static [_]
    false)
  (-eval [_ {:keys [parameters] :as context} _ _]
    (let [value (get parameters name ::not-found)]
      (if (identical? ::not-found value)
        (throw-anom (parameter-value-not-found-anom context name))
        value)))
  (-form [_]
    `(~'param-ref ~name)))


(defn- find-parameter-def
  "Returns the parameter-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{parameter-defs :def} :parameters} name]
  (some #(when (= name (:name %)) %) parameter-defs))


(defn- parameter-def-not-found-anom [context name]
  (ba/incorrect
    (format "Parameter definition `%s` not found." name)
    :context context))


(defmethod core/compile* :elm.compiler.type/parameter-ref
  [{:keys [library] :as context} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (if-let [{:keys [name]} (find-parameter-def library name)]
    (->ParameterRef name)
    (throw-anom (parameter-def-not-found-anom context name))))

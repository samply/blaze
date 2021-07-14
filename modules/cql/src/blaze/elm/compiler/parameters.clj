(ns blaze.elm.compiler.parameters
  "7. Parameters"
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.elm.compiler.core :as core]
    [cognitect.anomalies :as anom]))


;; 7.1. ParameterDef
;;
;; Not needed because it's not an expression.


;; 7.2. ParameterRef
(defn- find-parameter-def
  "Returns the parameter-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{parameter-defs :def} :parameters} name]
  (some #(when (= name (:name %)) %) parameter-defs))


(defrecord ParameterRef [name]
  core/Expression
  (-eval [_ {:keys [parameters] :as context} _ _]
    (let [value (get parameters name ::not-found)]
      (if-not (identical? ::not-found value)
        value
        (throw-anom
          ::anom/incorrect
          (format "Parameter `%s` not found." name)
          :context context)))))


(defmethod core/compile* :elm.compiler.type/parameter-ref
  [{:keys [library] :as context} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (if-let [{:keys [name]} (find-parameter-def library name)]
    (->ParameterRef name)
    (throw-anom
      ::anom/incorrect
      (format "Parameter `%s` not found." name)
      :context context)))

(ns blaze.elm.compiler.clinical-values
  "3. Clinical Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.elm.code :as code]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.concept :refer [concept]]
   [blaze.elm.date-time :as date-time]
   [blaze.elm.quantity :refer [quantity]]
   [blaze.elm.ratio :refer [ratio]]
   [blaze.elm.value-set :as value-set]))

(defn- find-code-system-def
  "Returns the code-system-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{code-system-defs :def} :codeSystems} name]
  (some #(when (= name (:name %)) %) code-system-defs))

;; 3.1. Code
(defn- code-system-not-found-anom [name context expression]
  (ba/not-found
   (format "Can't find the code system `%s`." name)
   :context context
   :expression expression))

(defmethod core/compile* :elm.compiler.type/code
  [{:keys [library] :as context}
   {{system-name :name} :system :keys [code] :as expression}]
  ;; TODO: look into other libraries (:libraryName)
  (if-let [{system :id :keys [version]} (find-code-system-def library system-name)]
    (code/code system version code)
    (throw-anom (code-system-not-found-anom system-name context expression))))

;; 3.2. CodeDef
;;
;; Not needed because it's not an expression.

;; 3.3. CodeRef
(defn- find-code-def
  "Returns the code-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{code-defs :def} :codes} name]
  (some #(when (= name (:name %)) %) code-defs))

(defmethod core/compile* :elm.compiler.type/code-ref
  [{:keys [library] :as context} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (when-let [{code-system-ref :codeSystem code :id}
             (find-code-def library name)]
    (when code-system-ref
      (when-let [{system :id :keys [version]} (core/compile* context (assoc code-system-ref :type "CodeSystemRef"))]
        (code/code system version code)))))

;; 3.4. CodeSystemDef
;;
;; Not needed because it's not an expression.

;; 3.5. CodeSystemRef
(defmethod core/compile* :elm.compiler.type/code-system-ref
  [{:keys [library]} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (find-code-system-def library name))

;; 3.6. Concept
(defn- compile-codes [context codes]
  (map #(core/compile* context %) codes))

(defmethod core/compile* :elm.compiler.type/concept
  [context {:keys [codes]}]
  (concept (compile-codes context codes)))

;; 3.7. ConceptDef
;;
;; Not needed because it's not an expression.

;; 3.8. ConceptRef
(defn- find-concept-def
  "Returns the concept-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{concept-defs :def} :concepts} name]
  (some #(when (= name (:name %)) %) concept-defs))

(defmethod core/compile* :elm.compiler.type/concept-ref
  [{:keys [library] :as context} {:keys [name]}]
  (when-let [{codes-refs :code} (find-concept-def library name)]
    (concept (mapv #(core/compile* context (assoc % :type "CodeRef")) codes-refs))))

;; 3.9. Quantity
(defmethod core/compile* :elm.compiler.type/quantity
  [_ {:keys [value unit] :or {unit "1"}}]
  (when value
    (case unit
      ("year" "years") (date-time/period value 0 0)
      ("month" "months") (date-time/period 0 value 0)
      ("week" "weeks") (date-time/period 0 0 (* value 7 24 3600000))
      ("day" "days") (date-time/period 0 0 (* value 24 3600000))
      ("hour" "hours") (date-time/period 0 0 (* value 3600000))
      ("minute" "minutes") (date-time/period 0 0 (* value 60000))
      ("second" "seconds") (date-time/period 0 0 (* value 1000))
      ("millisecond" "milliseconds") (date-time/period 0 0 value)
      (quantity value unit))))

;; 3.10. Ratio
(defmethod core/compile* :elm.compiler.type/ratio
  [_ {:keys [numerator denominator]}]
  (ratio (quantity (:value numerator) (or (:unit numerator) "1"))
         (quantity (:value denominator) (or (:unit denominator) "1"))))

;; 3.11. ValueSetDef
;;
;; Not needed because it's not an expression.

;; 3.12. ValueSetRef
(defn- find-value-set-def
  "Returns the value-set-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{value-set-defs :def} :valueSets} name]
  (some #(when (= name (:name %)) %) value-set-defs))

(def ^:private unsupported-anom
  (ba/unsupported "Terminology operations are not supported. Please enable either the external or the internal terminology service."))

(defmethod core/compile* :elm.compiler.type/value-set-ref
  [{:keys [library terminology-service]} {:keys [name]}]
  (when-let [{:keys [id]} (find-value-set-def library name)]
    (if terminology-service
      (value-set/value-set terminology-service id)
      (throw-anom unsupported-anom))))

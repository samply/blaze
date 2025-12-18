(ns blaze.elm.compiler.clinical-values
  "3. Clinical Values

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.anomaly :as ba :refer [throw-anom]]
   [blaze.elm.code :as code]
   [blaze.elm.code-system :as code-system]
   [blaze.elm.compiler.core :as core]
   [blaze.elm.concept :refer [concept]]
   [blaze.elm.date-time :as date-time]
   [blaze.elm.quantity :refer [quantity]]
   [blaze.elm.ratio :refer [ratio]]
   [blaze.elm.value-set :as value-set]))

;; 3.1. Code
(defmethod core/compile* :elm.compiler.type/code
  [context {:keys [system code]}]
  (let [{:keys [system version]} (core/compile* context system)]
    (code/code system version code)))

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
      (when-let [{:keys [system version]} (core/compile* context (assoc code-system-ref :type "CodeSystemRef"))]
        (code/code system version code)))))

;; 3.4. CodeSystemDef
;;
;; Not needed because it's not an expression.

;; 3.5. CodeSystemRef
(defn- find-code-system-def
  "Returns the code-system-def with `name` from `library` or nil if not found."
  {:arglists '([library name])}
  [{{code-system-defs :def} :codeSystems} name]
  (some #(when (= name (:name %)) %) code-system-defs))

(defn- code-system-not-found-anom [name context]
  (ba/not-found
   (format "Can't find the code system `%s`." name)
   :context context))

(defmethod core/compile* :elm.compiler.type/code-system-ref
  [{:keys [library terminology-service] :as context} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (if-let [def (find-code-system-def library name)]
    (code-system/code-system terminology-service def)
    (throw-anom (code-system-not-found-anom name context))))

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

(defn- value-set-not-found-anom [name context]
  (ba/not-found
   (format "Can't find the value set `%s`." name)
   :context context))

(defmethod core/compile* :elm.compiler.type/value-set-ref
  [{:keys [library terminology-service] :as context} {:keys [name]}]
  ;; TODO: look into other libraries (:libraryName)
  (if-let [{:keys [id]} (find-value-set-def library name)]
    (value-set/value-set terminology-service id)
    (value-set-not-found-anom name context)))

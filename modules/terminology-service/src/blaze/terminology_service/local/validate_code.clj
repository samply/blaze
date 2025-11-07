(ns blaze.terminology-service.local.validate-code
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]
   [cognitect.anomalies :as anom]))

(defn issue-anom-clause [{:keys [code system version]} issue]
  (let [{{:keys [text]} :details :as issue} issue]
    (cond-> (ba/not-found (type/value text) :code code :issues [issue])
      system (assoc :system system)
      version (assoc :version version))))

(defn issue-anom-concept [{:keys [code system version display inactive]} issues]
  (let [[{{:keys [text]} :details}] issues]
    (cond-> (ba/not-found
             (type/value text)
             :code (type/value code)
             :system (type/value system)
             :issues issues)
      version (assoc :version (type/value version))
      display (assoc :display (type/value display))
      inactive (assoc :inactive (type/value inactive)))))

(defn- designation-pred
  ([display]
   (fn [{:keys [value]}]
     (when (= display (type/value value))
       value)))
  ([display languages]
   (fn [{:keys [value language]}]
     (when (and (= display (type/value value))
                (languages (type/value language)))
       value))))

(defn- check-display*
  {:arglists '([params concept])}
  [{{:keys [display] :as clause} :clause languages :display-languages
    :keys [lenient-display-validation]}
   {designations :designation :as concept}]
  (if (= display (type/value (:display concept)))
    (assoc concept ::found-display (:display concept))
    (let [pred (if languages
                 (designation-pred display (set languages))
                 (designation-pred display))]
      (if-let [display (some pred designations)]
        (cond-> concept
          languages (assoc ::found-display display))
        (cond-> (issue-anom-concept concept [(issue/invalid-display clause concept lenient-display-validation)])
          lenient-display-validation (assoc :result-override true))))))

(defn- merge-concept [concept {designations :designation}]
  (update concept :designation (fnil into []) designations))

(defn- enhance-concept [supplements concept]
  (reduce
   (fn [{:keys [code] :as concept} {{:keys [concepts]} :default/graph}]
     (let [concept-supplement (concepts (type/value code))]
       (cond-> concept concept-supplement (merge-concept concept-supplement))))
   concept
   supplements))

(defn check-display
  {:arglists '([context params concept])}
  [{:keys [supplements]} params concept]
  (check-display* params (enhance-concept supplements concept)))

(defn- display [{::keys [found-display] :keys [display] designations :designation}]
  (or found-display
      display
      (:value (first designations))))

(defn parameters-from-concept
  {:arglists '([concept params])}
  [{:keys [code system version inactive] :as concept}
   {{:keys [origin] :as clause} :clause}]
  (fu/parameters
   "result" #fhir/boolean true
   "code" code
   "system" system
   "version" version
   "display" (some-> (display concept) type/string)
   "inactive" (some-> inactive type/boolean)
   "codeableConcept"
   (when (= "CodeableConcept.coding[0]" origin)
     (type/codeable-concept
      {:coding
       [(type/coding
         (cond->
          {:system (type/uri (:system clause))
           :code (type/code (:code clause))}
           (:version clause) (assoc :version (type/string (:version clause)))
           (:display clause) (assoc :display (type/string (:display clause)))))]}))))

(defn fail-parameters-from-anom
  [{::anom/keys [message]
    :keys [code system version display inactive issues result-override]}
   {{:keys [origin] :as clause} :clause}]
  (let [codeable-concept? (= "CodeableConcept.coding[0]" origin)]
    (fu/parameters
     "result" (type/boolean (or result-override false))
     "message" (type/string message)
     "code" (when-not codeable-concept? (some-> code type/code))
     "system" (when-not codeable-concept? (some-> system type/uri))
     "version" (some-> version type/string)
     "display" (some-> display type/string)
     "inactive" (some-> inactive type/boolean)
     "issues" {:fhir/type :fhir/OperationOutcome :issue issues}
     "codeableConcept"
     (when codeable-concept?
       (type/codeable-concept
        {:coding
         [(type/coding
           (cond->
            {:system (type/uri (:system clause))
             :code (type/code (:code clause))}
             (:version clause) (assoc :version (type/string (:version clause)))
             (:display clause) (assoc :display (type/string (:display clause)))))]})))))

(ns blaze.terminology-service.local.validate-code
  (:require
   [blaze.anomaly :as ba]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]))

(defn issue-anom-clause [{:keys [code system version]} issue]
  (let [{{:keys [text]} :details :as issue} issue]
    (cond-> (ba/not-found (:value text) :code code :issues [issue])
      system (assoc :system system)
      version (assoc :version version))))

(defn- details-texts [issues]
  (str/join " " (keep (comp :value :text :details) issues)))

(defn issues-anom-clause [{:keys [code system version]} issues]
  (cond-> (ba/not-found (details-texts issues) :code code :issues issues)
    system (assoc :system system)
    version (assoc :version version)))

(defn issue-anom-concept [{:keys [code system version display inactive]} issue]
  (let [{{:keys [text]} :details :as issue} issue]
    (cond-> (ba/not-found
             (:value text)
             :code (:value code)
             :system (:value system)
             :issues [issue])
      version (assoc :version (:value version))
      display (assoc :display (:value display))
      inactive (assoc :inactive (:value inactive)))))

(defn- designation-pred
  ([display]
   (fn [{:keys [value]}]
     (when (= display (:value value))
       value)))
  ([display languages]
   (fn [{:keys [value language]}]
     (when (and (= display (:value value)) (languages (:value language)))
       value))))

(defn- check-display*
  {:arglists '([params concept])}
  [{{:keys [display] :as clause} :clause languages :display-languages
    :keys [lenient-display-validation]}
   {designations :designation :as concept}]
  (if (= display (:value (:display concept)))
    (assoc concept ::found-display (:display concept))
    (let [pred (if languages
                 (designation-pred display (set languages))
                 (designation-pred display))]
      (if-let [display (some pred designations)]
        (cond-> concept
          languages (assoc ::found-display display))
        (cond-> (issue-anom-concept concept (issue/invalid-display clause concept lenient-display-validation))
          lenient-display-validation (assoc :result-override true))))))

(defn- merge-concept [concept {designations :designation}]
  (update concept :designation (fnil into []) designations))

(defn- enhance-concept [supplements concept]
  (reduce
   (fn [{:keys [code] :as concept} {{:keys [concepts]} :default/graph}]
     (let [concept-supplement (concepts (:value code))]
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
   {:keys [codeable-concept]}]
  (fu/parameters
   "result" #fhir/boolean true
   "code" code
   "system" system
   "version" version
   "display" (some-> (display concept) type/string)
   "inactive" (some-> inactive type/boolean)
   "codeableConcept" codeable-concept))

(defn fail-parameters-from-anom
  [{::anom/keys [message]
    :keys [code system version display inactive issues result-override]}
   {:keys [codeable-concept]}]
  (fu/parameters
   "result" (type/boolean (or result-override false))
   "message" (some-> message type/string)
   "code" (when-not codeable-concept (some-> code type/code))
   "system" (when-not codeable-concept (some-> system type/uri-interned))
   "version" (some-> version type/string)
   "display" (some-> display type/string)
   "inactive" (some-> inactive type/boolean)
   "issues" {:fhir/type :fhir/OperationOutcome :issue issues}
   "codeableConcept" codeable-concept))

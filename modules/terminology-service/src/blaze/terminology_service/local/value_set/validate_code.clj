(ns blaze.terminology-service.local.value-set.validate-code
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as u]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]
   [cognitect.anomalies :as anom]))

(defn- code-system-clause [code system {:keys [system-version display]}]
  (cond-> {:code code :system system}
    system-version (assoc :version system-version)
    display (assoc :display display)))

(defn- code-clause [code {:keys [system-version display]}]
  (cond-> {:code code}
    system-version (assoc :version system-version)
    display (assoc :display display)))

(defn- coding-clause [value-set coding origin]
  (if-let [code (-> coding :code type/value)]
    (if-let [system (-> coding :system type/value)]
      (let [version (-> coding :version type/value)
            display (-> coding :display type/value)]
        (cond-> {:code code :system system :origin origin}
          version (assoc :version version)
          display (assoc :display display)))
      (let [clause {:code code :origin origin}
            {{:keys [text]} :details :as not-in-vs} (issue/not-in-vs value-set clause)]
        (ba/incorrect
         (type/value text)
         :code code
         :issues [not-in-vs (issue/missing-system clause)])))
    (ba/incorrect "Missing required parameter `coding.code`.")))

(defn- validate-params
  "Tries to extract :clause from `params`."
  [value-set
   {:keys [code system infer-system coding codeable-concept] :as params}]
  (cond
    code
    (cond
      system
      (assoc params :clause (code-system-clause code system params))

      infer-system
      (assoc params :clause (code-clause code params))

      :else
      (ba/incorrect "Missing required parameter `system`."))

    coding
    (when-ok [clause (coding-clause value-set coding "Coding")]
      (assoc params :clause clause))

    codeable-concept
    (let [{:keys [coding]} codeable-concept]
      (condp = (count coding)
        1 (when-ok [clause (coding-clause value-set (first coding) "CodeableConcept.coding[0]")]
            (assoc params :clause clause))
        0 (ba/incorrect "Incorrect parameter `codeableConcept` with no coding.")

        (ba/unsupported "Unsupported parameter `codeableConcept` with more than one coding.")))

    :else
    (ba/incorrect "Missing one of the parameters `code`, `coding` or `codeableConcept`.")))

(defn- anom-clause [{code-system-version :version} {:keys [code system version]}]
  (cond-> (ba/not-found (format "Code `%s` not found." code) :code code)
    system (assoc :system system)
    version (assoc :version version)
    (type/value code-system-version) (assoc :version (type/value code-system-version))))

(defn- issue-anom-clause [{:keys [code system version]} issue]
  (let [{{:keys [text]} :details :as issue} issue]
    (cond-> (ba/not-found (type/value text) :code code :issues [issue])
      system (assoc :system system)
      version (assoc :version version))))

(defn- issue-anom-concept [{:keys [code system version display inactive]} issue]
  (let [{{:keys [text]} :details :as issue} issue]
    (cond-> (ba/not-found
             (type/value text)
             :code (type/value code)
             :system (type/value system)
             :issues [issue])
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

(defn- check-display
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
        (assoc concept ::found-display display)
        (cond-> (issue-anom-concept concept (issue/invalid-display clause concept lenient-display-validation))
          lenient-display-validation (assoc :result-override true))))))

(defn- match-clause [system version clause]
  (cond
    (nil? (:system clause))
    (cond-> (assoc clause :system system) version (assoc :version version))

    (and (= system (:system clause)) (nil? (:version clause)))
    (cond-> clause version (assoc :version version))

    (and (= system (:system clause)) (nil? version))
    clause

    (and (= system (:system clause)) (= version (:version clause)))
    clause))

(defn- find-code-system [context {:keys [system version] :as clause}]
  (-> (if version
        (cs/find context system version)
        (cs/find context system))
      (ac/exceptionally
       (fn [_]
         (-> (issue-anom-clause clause (issue/code-system-not-found clause))
             (update :issues conj (issue/unknown-code-system (:value-set context) clause))
             (assoc :terminal true))))))

(defn- find-filters [code-system filters params]
  (reduce
   #(if-ok [concept (cs/find-filter code-system %2 params)]
      (or concept (reduced nil))
      reduced)
   nil
   filters))

(defn- find-concept-include-system
  "Returns a CompletableFuture that will complete with the concept according to
  `clauses` in `context` or nil if not found or will complete exceptionally with
  an anomaly in case of errors.

  Only the code, system and version is compared here."
  [context
   {:keys [system version] concepts :concept filters :filter}
   {:keys [clause] :as params}]
  (if (= "*" (type/value version))
    (ac/completed-future (ba/unsupported "Validating codes in value set includes including all code system versions is unsupported."))
    (if-let [matched-clause (match-clause (type/value system) (type/value version) clause)]
      (if (and (seq concepts) (seq filters))
        (ac/completed-future (ba/incorrect "Incorrect combination of concept and filter."))
        (do-sync [code-system (find-code-system context matched-clause)]
          (cond
            #_#_(seq concepts) (validate-code-concepts code-system concepts code)
            (seq filters) (or (find-filters code-system filters (assoc params :clause matched-clause))
                              (anom-clause code-system matched-clause))
            :else (or (cs/find-complete code-system (assoc params :clause matched-clause))
                      (if (:system clause)
                        (issue-anom-clause matched-clause (issue/invalid-code matched-clause))
                        (issue-anom-clause clause (issue/cannot-infer code-system clause)))))))
      (ac/completed-future
       (-> (issue-anom-clause clause (issue/code-system-not-found clause))
           (assoc ::message-important true))))))

(defn- find-concept-in-value-set [context url {:keys [clause]}]
  (-> (vs/find context url)
      (ac/exceptionally
       (fn [_]
         (-> (issue-anom-clause clause (issue/value-set-not-found url))
             (update :issues conj (issue/unknown-value-set (:value-set context) url))
             (assoc :terminal true))))))

(defn- find-concept-in-value-sets [context [value-set & more] params]
  (-> (find-concept-in-value-set context (type/value value-set) params)
      (ac/handle
       (fn [concept e]
         (cond
           concept (ac/completed-future concept)
           (:terminal e) (ac/completed-future e)
           more (find-concept-in-value-sets context more params)
           :else (ac/completed-future e))))
      (ac/then-compose identity)))

(defn- find-concept-include
  [context {:keys [system] value-sets :valueSet :as include} params]
  (cond
    (and (type/value system) (seq value-sets))
    (ac/completed-future (ba/incorrect "Incorrect combination of system and valueSet."))

    (type/value system) (find-concept-include-system context include params)
    (seq value-sets) (find-concept-in-value-sets context value-sets params)

    :else (ac/completed-future (ba/incorrect "Missing system or valueSet."))))

(defn- find-concept-includes [context [include & more] params]
  (-> (find-concept-include context include params)
      (ac/handle
       (fn [concept e]
         (cond
           concept (ac/completed-future concept)
           (:terminal e) (ac/completed-future e)
           more (find-concept-includes context more params)
           :else (ac/completed-future e))))
      (ac/then-compose identity)))

(defn- state-validator [inactive {:keys [clause active-only]}]
  (if (or active-only (false? inactive))
    (fn [concept]
      (if (type/value (:inactive concept))
        (issue-anom-concept concept (issue/inactive-code clause))
        concept))
    identity))

(defn- display-validator [{{:keys [display]} :clause :as params}]
  (if display
    (partial check-display params)
    identity))

(defn- display [{::keys [found-display] :keys [display] designations :designation}]
  (or found-display
      display
      (:value (first designations))))

(defn- fail-parameters-from-anom
  [{::anom/keys [message]
    :keys [code system version display inactive issues result-override]}
   {:keys [origin] :as clause}]
  (u/parameters
   "result" (type/boolean (or result-override false))
   "message" (type/string message)
   "code" (some-> code type/code)
   "system" (some-> system type/uri)
   "version" (some-> version type/string)
   "display" (some-> display type/string)
   "inactive" (some-> inactive type/boolean)
   "issues" {:fhir/type :fhir/OperationOutcome :issue issues}
   "codeableConcept"
   (when (= "CodeableConcept.coding[0]" origin)
     (type/map->CodeableConcept
      {:coding
       [(type/map->Coding
         (cond->
          {:system (type/uri (:system clause))
           :code (type/code (:code clause))}
           (:version clause) (assoc :version (type/string (:version clause)))
           (:display clause) (assoc :display (type/string (:display clause)))))]}))))

(defn- validate-code*
  {:arglists '([context value-set params])}
  [context
   {{:keys [inactive] includes :include excludes :exclude} :compose :as value-set}
   {{:keys [origin] :as clause} :clause :as params}]
  (if (seq includes)
    (-> (find-concept-includes (assoc context :value-set value-set) includes params)
        (ac/then-apply
         (fn [concept]
           (if (seq excludes)
             (ba/unsupported "excludes are unsupported.")
             concept)))
        (ac/then-apply (state-validator inactive params))
        (ac/exceptionally
         (fn [e]
           (cond-> e
             (not (:terminal e))
             (as-> e (let [{{:keys [text]} :details :as issue} (issue/not-in-vs value-set clause)]
                       (cond-> (update e :issues (partial into [issue]))
                         (not (::message-important e)) (assoc ::anom/message text)))))))
        (ac/then-apply (display-validator params))
        (ac/then-apply
         (fn [{:keys [code system version] :as concept}]
           (u/parameters
            "result" #fhir/boolean true
            "code" code
            "system" system
            "version" version
            "display" (display concept)
            "codeableConcept"
            (when (= "CodeableConcept.coding[0]" origin)
              (type/map->CodeableConcept
               {:coding
                [(type/map->Coding
                  (cond->
                   {:system (type/uri (:system clause))
                    :code (type/code (:code clause))}
                    (:version clause) (assoc :version (type/string (:version clause)))
                    (:display clause) (assoc :display (type/string (:display clause)))))]})))))
        (ac/exceptionally #(fail-parameters-from-anom % clause)))
    (ac/completed-future
     (u/parameters
      "result" #fhir/boolean false
      "message" #fhir/string"Missing property `Valueset.compose.includes`."))))

(defn validate-code
  "Returns a CompletableFuture that will complete with a Parameters resource
  that contains the response of the validation request over `value-set`
  or will complete exceptionally with an anomaly in case of errors."
  [context value-set params]
  (if-ok [params (validate-params value-set params)]
    (validate-code* context value-set params)
    (comp ac/completed-future #(fail-parameters-from-anom % (:clause params)))))

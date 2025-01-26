(ns blaze.terminology-service.local.value-set.validate-code
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.validate-code :as vc]
   [blaze.terminology-service.local.value-set :as vs]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]
   [clojure.string :as str]
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

(defn- anom-clause
  ([{:keys [code system version]}]
   (cond-> (ba/not-found (format "Code `%s` not found." code) :code code)
     system (assoc :system system)
     version (assoc :version version)))
  ([{code-system-version :version} clause]
   (cond-> (anom-clause clause)
     (type/value code-system-version) (assoc :version (type/value code-system-version)))))

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
         (-> (vc/issue-anom-clause clause (issue/code-system-not-found clause))
             (update :issues conj (issue/unknown-code-system (:value-set context) clause))
             (assoc :terminal true))))))

(defn- find-filters [code-system filters params]
  (reduce
   (fn [_ filter]
     (let [res (cs/find-filter code-system filter params)]
       (cond-> res
         (or (nil? res) (ba/anomaly? res)) reduced)))
   nil
   filters))

(defn- find-complete [code-system {:keys [clause] :as params} matched-clause]
  (or (cs/find-complete code-system (assoc params :clause matched-clause))
      (if (:system clause)
        (vc/issue-anom-clause matched-clause (issue/invalid-code matched-clause))
        (vc/issue-anom-clause clause (issue/cannot-infer code-system clause)))))

(defn- find-concepts [code-system concepts {:keys [clause] :as params} matched-clause]
  (or (when-let [{:keys [code] :as concept} (cs/find-complete code-system (assoc params :clause matched-clause))]
        (when (some (comp #{(type/value code)} type/value :code) concepts)
          concept))
      (if (:system clause)
        (vc/issue-anom-clause matched-clause (issue/invalid-code matched-clause))
        (vc/issue-anom-clause clause (issue/cannot-infer code-system clause)))))

(defn- ignore-star-version [version]
  (when-not (= "*" version) version))

(defn- find-concept-include-system
  "Returns a CompletableFuture that will complete with the concept according to
  `clauses` in `context` or nil if not found or will complete exceptionally with
  an anomaly in case of errors.

  Only the code, system and version is compared here."
  [context
   {:keys [system version] concepts :concept filters :filter}
   {:keys [clause] :as params}]
  (if-let [matched-clause (match-clause (type/value system) (ignore-star-version (type/value version)) clause)]
    (if (and (seq concepts) (seq filters))
      (ac/completed-future (ba/incorrect "Incorrect combination of concept and filter."))
      (do-sync [code-system (find-code-system context matched-clause)]
        (cond
          (seq concepts) (find-concepts code-system concepts params matched-clause)
          (seq filters)
          (if-ok [concept (find-filters code-system filters (assoc params :clause matched-clause))]
            (or concept (anom-clause code-system matched-clause))
            #(-> (vc/issue-anom-clause matched-clause (issue/invalid-value-set (:value-set context) %))
                 (assoc :terminal true)))
          :else (find-complete code-system params matched-clause))))
    (ac/completed-future
     (-> (vc/issue-anom-clause clause (issue/code-system-not-found clause))
         (assoc ::message-important true)))))

(declare validate-code***)

(defn- find-concept-in-value-set [context url {:keys [clause] :as params}]
  (-> (vs/find context url)
      (ac/exceptionally
       (fn [_]
         (-> (vc/issue-anom-clause clause (issue/value-set-not-found url))
             (update :issues conj (issue/unknown-value-set (:value-set context) url))
             (assoc :terminal true))))
      (ac/then-compose #(validate-code*** context % params))))

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
        (vc/issue-anom-concept concept (issue/inactive-code clause))
        concept))
    identity))

(defn display-validator [context {{:keys [display]} :clause :as params}]
  (if display
    (partial vc/check-display context params)
    identity))

(defn- validate-code***
  {:arglists '([context value-set params])}
  [context
   {{:keys [inactive] includes :include excludes :exclude} :compose :as value-set}
   {:keys [clause] :as params}]
  (if (seq includes)
    (-> (find-concept-includes (assoc context :value-set value-set) includes params)
        (ac/then-compose
         (fn [concept]
           (-> (find-concept-includes (assoc context :value-set value-set) excludes params)
               (ac/handle
                (fn [_ e]
                  (if e
                    concept
                    (anom-clause clause)))))))
        (ac/then-apply (state-validator inactive params)))
    (anom-clause clause)))

(defn- validate-code**
  {:arglists '([context value-set params])}
  [context value-set {:keys [clause] :as params}]
  (-> (validate-code*** context value-set params)
      (ac/exceptionally
       (fn [e]
         (cond-> e
           (not (:terminal e))
           (as-> e (let [{{:keys [text]} :details :as issue} (issue/not-in-vs value-set clause)]
                     (cond-> (update e :issues (partial into [issue]))
                       (not (::message-important e)) (assoc ::anom/message text)))))))
      (ac/then-apply (display-validator context params))
      (ac/then-apply #(vc/parameters-from-concept % params))
      (ac/exceptionally #(vc/fail-parameters-from-anom % params))))

(defn- supplement-xf [context]
  (keep
   #(when (= "http://hl7.org/fhir/StructureDefinition/valueset-supplement" (:url %))
      (let [[url version] (str/split (type/value (:value %)) #"\|")
            context (assoc context ::cs/required-content #{"supplement"})]
        (if version
          (cs/find context url version)
          (cs/find context url))))))

(defn- supplements [context extensions]
  (let [futures (into [] (supplement-xf context) extensions)]
    (do-sync [_ (ac/all-of futures)]
      (mapv ac/join futures))))

(defn- validate-code* [context {extensions :extension :as value-set} params]
  (-> (supplements context extensions)
      (ac/then-compose
       #(validate-code** (assoc context :supplements %) value-set params))))

(defn validate-code
  "Returns a CompletableFuture that will complete with a Parameters resource
  that contains the response of the validation request over `value-set`
  or will complete exceptionally with an anomaly in case of errors."
  [context value-set params]
  (if-ok [params (validate-params value-set params)]
    (validate-code* context value-set params)
    (comp ac/completed-future #(vc/fail-parameters-from-anom % params))))

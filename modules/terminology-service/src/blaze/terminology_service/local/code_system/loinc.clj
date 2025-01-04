(ns blaze.terminology-service.local.code-system.loinc
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.loinc.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.util :as u]
   [taoensso.timbre :as log]))

(defn- code-system-not-found-msg [version]
  (format "The code system `%s|%s` was not found." url version))

(defmethod c/find :loinc
  [{:loinc/keys [context]} _ & [version]]
  (if (or (nil? version) (= version context/version))
    (ac/completed-future (assoc (first (:code-systems context)) :loinc/context context))
    (ba/not-found (code-system-not-found-msg version))))

(defmethod c/expand-complete :loinc
  [_ _]
  (ba/conflict
   "Expanding all LOINC concepts is too costly."
   :fhir/issue "too-costly"))

(defn- concept-xf [{{:keys [concept-index]} :loinc/context}]
  (keep
   (fn [{:keys [code]}]
     (concept-index (type/value code)))))

(defn- active-concept-xf [{{:keys [concept-index]} :loinc/context}]
  (keep
   (fn [{:keys [code]}]
     (when-let [concept (concept-index (type/value code))]
       (when-not (:inactive concept)
         concept)))))

(defn- remove-properties [concept]
  (dissoc concept :loinc/properties))

(defmethod c/expand-concept :loinc
  [code-system concepts {:keys [active-only]}]
  (into
   []
   (comp
    ((if active-only active-concept-xf concept-xf) code-system)
    (map remove-properties))
   concepts))

(defmulti expand-filter
  {:arglists '([code-system filter])}
  (fn [_ {:keys [op]}] (-> op type/value keyword)))

(defn- expand-filter-equals-class [{{:keys [class-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASS = filter value in code system `%s`." url))
    (class-index (keyword value))))

(defn- expand-filter-equals-status [{{:keys [status-index]} :loinc/context} value]
  (cond
    (#{"ACTIVE" "TRIAL" "DISCOURAGED" "DEPRECATED"} value)
    (status-index (keyword value))

    (nil? value)
    (ba/incorrect (format "Missing STATUS = filter value in code system `%s`." url))

    :else
    (ba/incorrect (format "Invalid STATUS = filter value `%s` in code system `%s`." value url))))

(defn- expand-filter-equals-class-type [{{:keys [class-type-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASSTYPE = filter value in code system `%s`." url))
    (if-ok [class-type (context/parse-class-type value)]
      (class-type-index class-type)
      (fn [_] (ba/incorrect (format "Invalid CLASSTYPE = filter value `%s` in code system `%s`." value url))))))

(defmethod expand-filter :=
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "CLASS" (expand-filter-equals-class code-system (type/value value))
    "STATUS" (expand-filter-equals-status code-system (type/value value))
    "CLASSTYPE" (expand-filter-equals-class-type code-system (type/value value))
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))

(defn expand-filter-regex-class [{{:keys [class-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASS regex filter value in code system `%s`." url))
    (into
     []
     (comp
      ;; should be a full match
      ;; see https://chat.fhir.org/#narrow/channel/179202-terminology/topic/ValueSet.20.60regex.60.20op
      (filter #(re-matches (re-pattern value) (name %)))
      (mapcat class-index))
     (keys class-index))))

(defmethod expand-filter :regex
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "CLASS" (expand-filter-regex-class code-system (type/value value))
    nil (ba/incorrect (format "Missing regex filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported regex filter property `%s` in code system `%s`." (type/value property) url))))

(defmethod expand-filter :default
  [_ {:keys [op]}]
  (ba/unsupported (format "Unsupported filter operator `%s` in code system `%s`." (type/value op) url)))

(defmethod c/expand-filter :loinc
  [code-system filter {:keys [active-only]}]
  (when-ok [concepts (expand-filter code-system filter)]
    (into
     #{}
     (comp
      (if active-only (filter (comp not :inactive)) identity)
      (map remove-properties))
     concepts)))

(defmethod c/find-complete :loinc
  [{:keys [version] {:keys [concept-index]} :loinc/context} {{:keys [code]} :clause}]
  (when-let [concept (concept-index code)]
    (-> (assoc concept :version version)
        (remove-properties))))

(defmulti satisfies-filter
  {:arglists '([filter code])}
  (fn [{:keys [op]} _] (-> op type/value keyword)))

(defn- satisfies-filter-equals-class [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASS = filter value in code system `%s`." url))
    (when (= (keyword value) (:class properties))
      concept)))

(defn- satisfies-filter-equals-status [value {:loinc/keys [properties] :as concept}]
  (cond
    (#{"ACTIVE" "TRIAL" "DISCOURAGED" "DEPRECATED"} value)
    (when (= (keyword value) (:status properties))
      concept)

    (nil? value)
    (ba/incorrect (format "Missing STATUS = filter value in code system `%s`." url))

    :else
    (ba/incorrect (format "Invalid STATUS = filter value `%s` in code system `%s`." value url))))

(defn- satisfies-filter-equals-class-type [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASSTYPE = filter value in code system `%s`." url))
    (if-ok [class-type (context/parse-class-type value)]
      (when (= class-type (:class-type properties))
        concept)
      (fn [_] (ba/incorrect (format "Invalid CLASSTYPE = filter value `%s` in code system `%s`." value url))))))

(defmethod satisfies-filter :=
  [{:keys [property value]} concept]
  (condp = (type/value property)
    "CLASS" (satisfies-filter-equals-class (type/value value) concept)
    "STATUS" (satisfies-filter-equals-status (type/value value) concept)
    "CLASSTYPE" (satisfies-filter-equals-class-type (type/value value) concept)
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))

(defn satisfies-filter-regex-class [value {{:keys [class]} :loinc/properties :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASS regex filter value in code system `%s`." url))
    (when (re-matches (re-pattern value) (name class))
      concept)))

(defmethod satisfies-filter :regex
  [{:keys [property value]} concept]
  (condp = (type/value property)
    "CLASS" (satisfies-filter-regex-class (type/value value) concept)
    nil (ba/incorrect (format "Missing regex filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported regex filter property `%s` in code system `%s`." (type/value property) url))))

(defmethod satisfies-filter :default
  [{:keys [op]} _]
  (ba/unsupported (format "Unsupported filter operator `%s` in code system `%s`." (type/value op) url)))

(defmethod c/find-filter :loinc
  [{{:keys [concept-index]} :loinc/context} filter {{:keys [code]} :clause}]
  (when-let [concept (concept-index code)]
    (some-> (satisfies-filter filter concept)
            (remove-properties))))

(defmethod c/satisfies-filter? :loinc
  [_ filter concept]
  (some? (satisfies-filter filter concept)))

(defn build-context
  "Builds the :loinc/context from classpath."
  []
  (context/build))

(defn ensure-code-systems
  "Ensures that all LOINC code systems are present in the database node."
  [{:keys [node] :as context} {:keys [code-systems]}]
  (-> (u/code-system-versions (d/db node) url)
      (ac/then-compose
       (fn [existing-versions]
         (let [tx-ops (u/tx-ops context existing-versions code-systems)]
           (if (seq tx-ops)
             (do (log/debug "Create" (count tx-ops) "new LOINC CodeSystem resources...")
                 (d/transact node tx-ops))
             (ac/completed-future nil)))))))

(ns blaze.terminology-service.local.code-system.loinc
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.loinc.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.util :as cs-u]
   [blaze.util :as u]
   [clojure.string :as str]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- code-system-not-found-msg [version]
  (format "The code system `%s|%s` was not found." url version))

(defmethod c/find :loinc
  [{:loinc/keys [context]} _ & [version]]
  (if (or (nil? version) (= version context/version))
    (ac/completed-future (assoc (first (:code-systems context)) :loinc/context context))
    (ac/completed-future (ba/not-found (code-system-not-found-msg version)))))

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

(defn- property-name-from-index [index]
  (condp = index
    :time-index "TIME_ASPCT"
    :scale-index "SCALE_TYP"
    :method-index "METHOD_TYP"
    (-> index name (str/split #"-") first str/upper-case)))

(defn- expand-filter-equals [{:loinc/keys [context]} index value]
  (if (nil? value)
    (ba/incorrect (format "Missing %s = filter value in code system `%s`."
                          (property-name-from-index index) url))
    (get-in context [index (str/upper-case value)])))

(defn- expand-filter-equals-status [{{:keys [status-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing STATUS = filter value in code system `%s`." url))
    (let [value-upper (str/upper-case value)]
      (if (#{"ACTIVE" "TRIAL" "DISCOURAGED" "DEPRECATED"} value-upper)
        (status-index (keyword value-upper))
        (ba/incorrect (format "Invalid STATUS = filter value `%s` in code system `%s`." value url))))))

(defn- expand-filter-equals-class-type [{{:keys [class-type-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASSTYPE = filter value in code system `%s`." url))
    (if-ok [class-type (context/parse-class-type value)]
      (class-type-index class-type)
      (fn [_] (ba/incorrect (format "Invalid CLASSTYPE = filter value `%s` in code system `%s`." value url))))))

(defn- expand-filter-equals-order-obs [{{:keys [order-obs-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing ORDER_OBS = filter value in code system `%s`." url))
    (if-ok [order-obs (context/parse-order-obs value)]
      (order-obs-index order-obs)
      (fn [_] (ba/incorrect (format "Invalid ORDER_OBS = filter value `%s` in code system `%s`." value url))))))

(defmethod expand-filter :=
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "COMPONENT" (expand-filter-equals code-system :component-index (type/value value))
    "PROPERTY" (expand-filter-equals code-system :property-index (type/value value))
    "TIME_ASPCT" (expand-filter-equals code-system :time-index (type/value value))
    "SYSTEM" (expand-filter-equals code-system :system-index (type/value value))
    "SCALE_TYP" (expand-filter-equals code-system :scale-index (type/value value))
    "METHOD_TYP" (expand-filter-equals code-system :method-index (type/value value))
    "CLASS" (expand-filter-equals code-system :class-index (type/value value))
    "STATUS" (expand-filter-equals-status code-system (type/value value))
    "CLASSTYPE" (expand-filter-equals-class-type code-system (type/value value))
    "ORDER_OBS" (expand-filter-equals-order-obs code-system (type/value value))
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))

(defn- case-insensitive-pattern [value]
  (re-pattern (str "(?i)" value)))

(defn- expand-filter-regex [{:loinc/keys [context]} index value]
  (if (nil? value)
    (ba/incorrect (format "Missing %s regex filter value in code system `%s`."
                          (property-name-from-index index) url))
    (let [index (index context)]
      (into
       []
       (comp
        ;; should be a full match
        ;; see https://chat.fhir.org/#narrow/channel/179202-terminology/topic/ValueSet.20.60regex.60.20op
        (filter #(re-matches (case-insensitive-pattern value) %))
        (mapcat index))
       (keys index)))))

(defn- expand-filter-regex-order-obs [{{:keys [order-obs-index]} :loinc/context} value]
  (if (nil? value)
    (ba/incorrect (format "Missing ORDER_OBS regex filter value in code system `%s`." url))
    (into
     []
     (comp
      ;; should be a full match
      ;; see https://chat.fhir.org/#narrow/channel/179202-terminology/topic/ValueSet.20.60regex.60.20op
      (filter #(re-matches (case-insensitive-pattern value) (name %)))
      (mapcat order-obs-index))
     (keys order-obs-index))))

(defmethod expand-filter :regex
  [code-system {:keys [property value]}]
  (condp = (type/value property)
    "COMPONENT" (expand-filter-regex code-system :component-index (type/value value))
    "PROPERTY" (expand-filter-regex code-system :property-index (type/value value))
    "TIME_ASPCT" (expand-filter-regex code-system :time-index (type/value value))
    "SYSTEM" (expand-filter-regex code-system :system-index (type/value value))
    "SCALE_TYP" (expand-filter-regex code-system :scale-index (type/value value))
    "METHOD_TYP" (expand-filter-regex code-system :method-index (type/value value))
    "CLASS" (expand-filter-regex code-system :class-index (type/value value))
    "ORDER_OBS" (expand-filter-regex-order-obs code-system (type/value value))
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

(defn- property-name-from-key [key]
  (condp = key
    :time "TIME_ASPCT"
    :scale "SCALE_TYP"
    :method "METHOD_TYP"
    (-> key name str/upper-case)))

(defn- satisfies-filter-equals [key value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing %s = filter value in code system `%s`."
                          (property-name-from-key key) url))
    (when (some #{(str/upper-case value)} (key properties))
      concept)))

(defn- satisfies-filter-equals-status [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing STATUS = filter value in code system `%s`." url))
    (let [value-upper (str/upper-case value)]
      (if (#{"ACTIVE" "TRIAL" "DISCOURAGED" "DEPRECATED"} value-upper)
        (when (= (keyword value-upper) (:status properties))
          concept)
        (ba/incorrect (format "Invalid STATUS = filter value `%s` in code system `%s`." value url))))))

(defn- satisfies-filter-equals-class-type [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing CLASSTYPE = filter value in code system `%s`." url))
    (if-ok [class-type (context/parse-class-type value)]
      (when (= class-type (:class-type properties))
        concept)
      (fn [_] (ba/incorrect (format "Invalid CLASSTYPE = filter value `%s` in code system `%s`." value url))))))

(defn- satisfies-filter-equals-order-obs [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing ORDER_OBS = filter value in code system `%s`." url))
    (if-ok [order-obs (context/parse-order-obs value)]
      (when (= order-obs (:order-obs properties))
        concept)
      (fn [_] (ba/incorrect (format "Invalid ORDER_OBS = filter value `%s` in code system `%s`." value url))))))

(defn- satisfies-filter-equals-list [value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing LIST = filter value in code system `%s`." url))
    (when (= value (:list properties))
      concept)))

(defmethod satisfies-filter :=
  [{:keys [property value]} concept]
  (condp = (type/value property)
    "COMPONENT" (satisfies-filter-equals :component (type/value value) concept)
    "PROPERTY" (satisfies-filter-equals :property (type/value value) concept)
    "TIME_ASPCT" (satisfies-filter-equals :time (type/value value) concept)
    "SYSTEM" (satisfies-filter-equals :system (type/value value) concept)
    "SCALE_TYP" (satisfies-filter-equals :scale (type/value value) concept)
    "METHOD_TYP" (satisfies-filter-equals :method (type/value value) concept)
    "CLASS" (satisfies-filter-equals :class (type/value value) concept)
    "STATUS" (satisfies-filter-equals-status (type/value value) concept)
    "CLASSTYPE" (satisfies-filter-equals-class-type (type/value value) concept)
    "ORDER_OBS" (satisfies-filter-equals-order-obs (type/value value) concept)
    "LIST" (satisfies-filter-equals-list (type/value value) concept)
    nil (ba/incorrect (format "Missing = filter property in code system `%s`." url))
    (ba/unsupported (format "Unsupported = filter property `%s` in code system `%s`." (type/value property) url))))

(defn- satisfies-filter-regex [key value {:loinc/keys [properties] :as concept}]
  (if (nil? value)
    (ba/incorrect (format "Missing %s regex filter value in code system `%s`."
                          (property-name-from-key key) url))
    (when (some (partial re-matches (case-insensitive-pattern value)) (key properties))
      concept)))

(defmethod satisfies-filter :regex
  [{:keys [property value]} concept]
  (condp = (type/value property)
    "COMPONENT" (satisfies-filter-regex :component (type/value value) concept)
    "PROPERTY" (satisfies-filter-regex :property (type/value value) concept)
    "TIME_ASPCT" (satisfies-filter-regex :time (type/value value) concept)
    "SCALE_TYP" (satisfies-filter-regex :scale (type/value value) concept)
    "METHOD_TYP" (satisfies-filter-regex :method (type/value value) concept)
    "SYSTEM" (satisfies-filter-regex :system (type/value value) concept)
    "CLASS" (satisfies-filter-regex :class (type/value value) concept)
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

(defmethod ig/init-key ::cs/loinc
  [_ _]
  (log/info "Start reading LOINC data...")
  (let [start (System/nanoTime)
        context (ba/throw-when (context/build))]
    (log/info "Successfully read LOINC data in"
              (format "%.1f" (u/duration-s start)) "seconds")
    context))

(defn ensure-code-systems
  "Ensures that all LOINC code systems are present in the database node."
  {:arglists '([context sct-context])}
  [{:keys [node] :as context} {:keys [code-systems]}]
  (-> (cs-u/code-system-versions (d/db node) url)
      (ac/then-compose
       (fn [existing-versions]
         (let [tx-ops (cs-u/tx-ops context existing-versions code-systems)]
           (if (seq tx-ops)
             (do (log/debug "Create" (count tx-ops) "new LOINC CodeSystem resources...")
                 (d/transact node tx-ops))
             (ac/completed-future nil)))))))

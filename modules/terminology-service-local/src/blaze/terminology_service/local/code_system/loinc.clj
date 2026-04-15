(ns blaze.terminology-service.local.code-system.loinc
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.loinc.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.loinc.filter.core :as core]
   [blaze.terminology-service.local.code-system.loinc.filter.equals]
   [blaze.terminology-service.local.code-system.loinc.filter.regex]
   [blaze.terminology-service.local.code-system.util :as cs-u]
   [blaze.terminology-service.local.search-index :as search-index]
   [blaze.util :as u]
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

(defn- remove-properties [concept]
  (dissoc concept :loinc/properties))

(defn- concept-xf [{{:keys [concept-index]} :loinc/context}]
  (comp
   (keep
    (fn [{:keys [code]}]
      (concept-index (:value code))))
   (map remove-properties)))

(defn- active-concept-xf [{{:keys [concept-index]} :loinc/context}]
  (comp
   (keep
    (fn [{:keys [code]}]
      (when-let [concept (concept-index (:value code))]
        (when-not (-> concept :inactive :value)
          concept))))
   (map remove-properties)))

(def ^:private active-xf
  (comp (filter (comp not :value :inactive))
        (map remove-properties)))

(def ^:private xf
  (map remove-properties))

(defn- complete-text-filter
  [{{:keys [concept-index search-index]} :loinc/context} filter]
  (keep concept-index (search-index/search search-index filter 10000)))

(defmethod c/expand-complete :loinc
  [code-system {:keys [active-only filter]}]
  (if filter
    (into
     []
     (if active-only active-xf xf)
     (complete-text-filter code-system filter))
    (ba/conflict
     "Expanding all LOINC concepts is too costly."
     :fhir/issue "too-costly")))

(defn- concept-text-filter
  [{{:keys [search-index]} :loinc/context} filter value-set-concepts]
  (let [lookup (persistent! (reduce #(assoc! %1 (:value (:code %2)) %2) (transient {}) value-set-concepts))
        matched-codes (search-index/search search-index filter 10000 (keys lookup))]
    (keep lookup matched-codes)))

(defmethod c/expand-concept :loinc
  [code-system value-set-concepts {:keys [active-only filter]}]
  (into
   []
   ((if active-only active-concept-xf concept-xf) code-system)
   (cond->> value-set-concepts
     filter (concept-text-filter code-system filter))))

(defn- filter-text-filter
  [{{:keys [concept-index search-index]} :loinc/context} filter found-concepts]
  (let [found-codes (mapv (comp :value :code) found-concepts)
        matched-codes (search-index/search search-index filter 10000 found-codes)]
    (keep concept-index matched-codes)))

(defmethod c/expand-filter :loinc
  [code-system filter {:keys [active-only] text-filter :filter}]
  (when-ok [found-concepts (core/expand-filter code-system filter)]
    (into
     #{}
     (if active-only active-xf xf)
     (cond->> found-concepts
       text-filter (filter-text-filter code-system text-filter)))))

(defmethod c/find-complete :loinc
  [{:keys [version] {:keys [concept-index]} :loinc/context} {{:keys [code]} :clause}]
  (when-let [concept (concept-index code)]
    (-> (assoc concept :version version)
        (remove-properties))))

(defmethod c/find-filter :loinc
  [{{:keys [concept-index]} :loinc/context :as code-system} filter {{:keys [code]} :clause}]
  (when-let [concept (concept-index code)]
    (some-> (core/satisfies-filter code-system filter concept)
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
  {:arglists '([context loinc-context])}
  [{:keys [node] :as context} {:keys [code-systems]}]
  (-> (cs-u/code-system-versions (d/db node) url)
      (ac/then-compose
       (fn [existing-versions]
         (let [tx-ops (cs-u/tx-ops context existing-versions code-systems)]
           (if (seq tx-ops)
             (do (log/debug "Create" (count tx-ops) "new LOINC CodeSystem resources...")
                 (d/transact node tx-ops))
             (ac/completed-future nil)))))))

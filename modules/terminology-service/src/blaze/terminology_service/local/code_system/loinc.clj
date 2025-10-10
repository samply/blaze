(ns blaze.terminology-service.local.code-system.loinc
  (:require
   [blaze.anomaly :as ba :refer [when-ok]]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.loinc.context :as context :refer [url]]
   [blaze.terminology-service.local.code-system.loinc.filter.core :as core]
   [blaze.terminology-service.local.code-system.loinc.filter.equals]
   [blaze.terminology-service.local.code-system.loinc.filter.regex]
   [blaze.terminology-service.local.code-system.util :as cs-u]
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
       (when-not (-> concept :inactive type/value)
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

(defmethod c/expand-filter :loinc
  [code-system filter {:keys [active-only]}]
  (when-ok [concepts (core/expand-filter code-system filter)]
    (into
     #{}
     (comp
      (if active-only (filter (comp not type/value :inactive)) identity)
      (map remove-properties))
     concepts)))

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

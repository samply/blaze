(ns blaze.terminology-service.local.code-system.default
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as u]
   [blaze.terminology-service.local :as-alias local]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.filter.core :as filter]
   [blaze.terminology-service.local.code-system.filter.descendent-of]
   [blaze.terminology-service.local.code-system.filter.equals]
   [blaze.terminology-service.local.code-system.filter.exists]
   [blaze.terminology-service.local.code-system.filter.is-a]
   [blaze.terminology-service.local.code-system.filter.regex]
   [blaze.terminology-service.local.graph :as graph]
   [blaze.terminology-service.local.priority :as priority]
   [cognitect.anomalies :as anom]))

(defn- code-system-not-complete-msg [{:keys [url content]}]
  (format "Can't use the code system `%s` because it is not complete. It's content is `%s`."
          url (type/value content)))

(defn- code-system-not-complete-anom [code-system]
  (ba/conflict (code-system-not-complete-msg code-system)))

(defmethod c/find :default
  [{:keys [db]} url & [version]]
  (do-sync [code-systems (d/pull-many db (d/type-query db "CodeSystem" (cond-> [["url" url]] version (conj ["version" version]))))]
    (if-let [{:keys [content] concepts :concept :as code-system} (first (priority/sort-by-priority code-systems))]
      (if (= "complete" (type/value content))
        (assoc code-system :default/graph (graph/build-graph concepts))
        (code-system-not-complete-anom code-system))
      (ba/not-found
       (if version
         (format "The code system `%s` with version `%s` was not found." url version)
         (format "The code system `%s` was not found." url))
       ::local/category :code-system-not-found
       :code-system/url url))))

(defmethod c/enhance :default
  [_ {concepts :concept :as code-system}]
  (assoc code-system :default/graph (graph/build-graph concepts)))

(defn- not-found-msg [{:keys [url]} code]
  (if url
    (format "The provided code `%s` was not found in the code system `%s`." code (type/value url))
    (format "The provided code `%s` was not found in the provided code system." code)))

(defn- find-concept [{{:keys [concepts]} :default/graph :as code-system} {:keys [code]}]
  (or (concepts code)
      (ba/not-found (not-found-msg code-system code))))

(defmethod c/validate-code :default
  [{:keys [url version] :as code-system} {:keys [clause]}]
  (if-ok [{:keys [code]} (find-concept code-system clause)]
    (u/parameters
     "result" #fhir/boolean true
     "code" code
     "system" url
     "version" version)
    (fn [{::anom/keys [message]}]
      (u/parameters
       "result" #fhir/boolean false
       "message" (type/string message)))))

(defn- inactive? [{properties :property}]
  (some
   (fn [{:keys [code value]}]
     (condp = (type/value code)
       "status" (when (= "retired" (type/value value)) true)
       "inactive" (when-some [value (type/value value)] value)
       nil))
   properties))

(defn- not-selectable? [{properties :property}]
  (some
   (fn [{:keys [code value]}]
     (and (= "notSelectable" (type/value code))
          (type/value value)))
   properties))

(defn- definition-property [definition]
  {:fhir/type :fhir.ValueSet.expansion.contains/property
   :code #fhir/code"definition"
   :value definition})

(defn- create-contains
  [{:keys [include-designations properties]}
   url
   {:keys [code display definition] :as concept}]
  (let [normal-properties (remove #{"definition"} properties)]
    (cond-> {:system url :code code}
      display (assoc :display display)
      (inactive? concept) (assoc :inactive #fhir/boolean true)
      (not-selectable? concept) (assoc :abstract #fhir/boolean true)
      include-designations (assoc :designation (:designation concept))
      (seq normal-properties) (assoc :property (filterv (comp (set normal-properties) type/value :code) (:property concept)))
      (and definition (some #{"definition"} properties)) (update :property (fnil conj []) (definition-property definition)))))

(defn- xf [params {:keys [url]}]
  (map
   (fn [concept]
     (create-contains params url concept))))

(defn- active-xf [params {:keys [url]}]
  (keep
   (fn [concept]
     (when-not (inactive? concept)
       (create-contains params url concept)))))

(defmethod c/expand-complete :default
  [{{:keys [concepts]} :default/graph :as code-system}
   {:keys [active-only] :as params}]
  (into
   []
   ((if active-only active-xf xf) params code-system)
   (vals concepts)))

(defn- concept-xf [params {:keys [url] {:keys [concepts]} :default/graph}]
  (keep
   (fn [{:keys [code display]}]
     (when-let [concept (concepts (type/value code))]
       (cond-> (create-contains params url concept)
         display (assoc :display display))))))

(defn- concept-active-xf [params {:keys [url] {:keys [concepts]} :default/graph}]
  (keep
   (fn [{:keys [code display]}]
     (when-let [concept (concepts (type/value code))]
       (when-not (inactive? concept)
         (cond-> (create-contains params url concept)
           display (assoc :display display)))))))

(defmethod c/expand-concept :default
  [code-system value-set-concepts {:keys [active-only] :as params}]
  (into
   []
   ((if active-only concept-active-xf concept-xf) params code-system)
   value-set-concepts))

(defmethod c/expand-filter :default
  [code-system filter {:keys [active-only] :as params}]
  (when-ok [concepts (filter/filter-concepts filter code-system)]
    (into
     #{}
     ((if active-only active-xf xf) params code-system)
     concepts)))

(defmethod c/find-complete :default
  [{:keys [url version] {:keys [concepts]} :default/graph}
   {{:keys [code]} :clause}]
  (when-let [concept (concepts code)]
    (cond-> (assoc concept :system url)
      version (assoc :version version)
      (inactive? concept) (assoc :inactive #fhir/boolean true))))

(defmethod c/find-filter :default
  [{:keys [url version] :as code-system} filter {{:keys [code]} :clause}]
  (when-ok [concept (filter/find-concept filter code-system code)]
    (when concept
      (cond-> (assoc concept :system url)
        version (assoc :version version)
        (inactive? concept) (assoc :inactive #fhir/boolean true)))))

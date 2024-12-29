(ns blaze.terminology-service.local.code-system.default
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.filter.core :as filter]
   [blaze.terminology-service.local.code-system.filter.descendent-of]
   [blaze.terminology-service.local.code-system.filter.equals]
   [blaze.terminology-service.local.code-system.filter.exists]
   [blaze.terminology-service.local.code-system.filter.is-a]
   [blaze.terminology-service.local.code-system.filter.regex]
   [blaze.terminology-service.local.code-system.util :as u]
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
         (format "The code system `%s` was not found." url))))))

(defmethod c/enhance :default
  [_ {concepts :concept :as code-system}]
  (assoc code-system :default/graph (graph/build-graph concepts)))

(defn- concept-pred [code]
  (fn [concept]
    (when (= code (type/value (:code concept)))
      concept)))

(defn- not-found-msg [{:keys [url]} code]
  (if url
    (format "The provided code `%s` was not found in the code system `%s`." code (type/value url))
    (format "The provided code `%s` was not found in the provided code system." code)))

(defn- find-concept
  [{concepts :concept :as code-system} code]
  (or (some (concept-pred code) concepts)
      (ba/not-found (not-found-msg code-system code))))

(defmethod c/validate-code :default
  [{:keys [url] :as code-system} request]
  (if-ok [code (u/extract-code request (type/value url))
          {:keys [code]} (find-concept code-system code)]
    {:fhir/type :fhir/Parameters
     :parameter
     (cond->
      [(u/parameter "result" #fhir/boolean true)
       (u/parameter "code" code)]
       url (conj (u/parameter "system" url)))}
    (fn [{::anom/keys [message]}]
      {:fhir/type :fhir/Parameters
       :parameter
       [(u/parameter "result" #fhir/boolean false)
        (u/parameter "message" (type/string message))]})))

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

(defn- create-contains [url {:keys [code display] :as concept}]
  (cond-> {:system url :code code}
    display (assoc :display display)
    (inactive? concept) (assoc :inactive #fhir/boolean true)
    (not-selectable? concept) (assoc :abstract #fhir/boolean true)))

(defn- xf [{:keys [url]}]
  (map
   (fn [concept]
     (create-contains url concept))))

(defn- active-xf [{:keys [url]}]
  (keep
   (fn [concept]
     (when-not (inactive? concept)
       (create-contains url concept)))))

(defmethod c/expand-complete :default
  [{:keys [active-only]} inactive {{:keys [concepts]} :default/graph :as code-system}]
  (into
   []
   ((if (or active-only (false? inactive)) active-xf xf) code-system)
   (vals concepts)))

(defn- concept-xf [{:keys [url] {:keys [concepts]} :default/graph}]
  (keep
   (fn [{:keys [code display]}]
     (when-let [concept (concepts (type/value code))]
       (cond-> (create-contains url concept)
         display (assoc :display display))))))

(defn- concept-active-xf [{:keys [url] {:keys [concepts]} :default/graph}]
  (keep
   (fn [{:keys [code display]}]
     (when-let [concept (concepts (type/value code))]
       (when-not (inactive? concept)
         (cond-> (create-contains url concept)
           display (assoc :display display)))))))

(defmethod c/expand-concept :default
  [{:keys [active-only]} inactive code-system value-set-concepts]
  (into
   []
   ((if (or active-only (false? inactive)) concept-active-xf concept-xf) code-system)
   value-set-concepts))

(defmethod c/expand-filter :default
  [{:keys [active-only]} inactive code-system filter]
  (when-ok [concepts (filter/filter-concepts filter code-system)]
    (into
     #{}
     ((if (or active-only (false? inactive)) active-xf xf) code-system)
     concepts)))

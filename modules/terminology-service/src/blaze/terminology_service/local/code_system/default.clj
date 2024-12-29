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
   [blaze.terminology-service.local.code-system.util :as u]
   [blaze.terminology-service.local.graph :as graph]
   [blaze.terminology-service.local.priority :as priority]
   [cognitect.anomalies :as anom]))

(defmethod c/find :default
  [{:keys [db]} url & [version]]
  (do-sync [code-systems (d/pull-many db (d/type-query db "CodeSystem" (cond-> [["url" url]] version (conj ["version" version]))))]
    (if-let [{concepts :concept :as code-system} (first (priority/sort-by-priority code-systems))]
      (assoc code-system :default/graph (graph/build-graph concepts))
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

(defn- check-complete [{:keys [content] :as code-system}]
  (when-not (= "complete" (type/value content))
    (ba/incorrect (format "Can't use the code system `%s` because it is not complete. It's content is `%s`." (type/value (:url code-system)) (type/value content)))))

(defmethod c/validate-code :default
  [{:keys [url] :as code-system} request]
  (if-ok [_ (check-complete code-system)
          code (u/extract-code request (type/value url))
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

(defn- code-system-not-complete-msg [{:keys [url content]}]
  (format "Can't expand the code system `%s` because it is not complete. It's content is `%s`."
          url (type/value content)))

(defn- code-system-not-complete-anom [code-system]
  (ba/unsupported (code-system-not-complete-msg code-system) :http/status 409))

(defn- check-code-system-content [{:keys [content] :as code-system}]
  (when-not (= "complete" (type/value content))
    (code-system-not-complete-anom code-system)))

(defn- inactive? [{properties :property}]
  (some
   (fn [{:keys [code value]}]
     (and (= "inactive" (type/value code))
          (type/value value)))
   properties))

(defn- complete-xf [{:keys [url]}]
  (map
   (fn [{:keys [code display] :as concept}]
     (cond-> {:system url :code code}
       display (assoc :display display)
       (inactive? concept) (assoc :inactive #fhir/boolean true)))))

(defn- active-complete-xf [{:keys [url]}]
  (keep
   (fn [{:keys [code display] :as concept}]
     (when-not (inactive? concept)
       (cond-> {:system url :code code}
         display (assoc :display display))))))

(defmethod c/expand-complete :default
  [{:keys [active-only]} {concepts :concept :as code-system}]
  (when-ok [_ (check-code-system-content code-system)]
    (into
     []
     ((if active-only active-complete-xf complete-xf) code-system)
     concepts)))

(defn- concept-xf [{:keys [url] {:keys [concepts]} :default/graph}]
  (keep
   (fn [{:keys [code] vs-display :display}]
     (when-let [{:keys [display] :as concept} (concepts (type/value code))]
       (let [display (or vs-display display)]
         (cond-> {:system url :code code}
           display (assoc :display display)
           (inactive? concept) (assoc :inactive #fhir/boolean true)))))))

(defn- active-concept-xf [{:keys [url] {:keys [concepts]} :default/graph}]
  (keep
   (fn [{:keys [code] vs-display :display}]
     (when-let [{:keys [display] :as concept} (concepts (type/value code))]
       (let [display (or vs-display display)]
         (when-not (inactive? concept)
           (cond-> {:system url :code code}
             display (assoc :display display))))))))

(defmethod c/expand-concept :default
  [{:keys [active-only]} code-system value-set-concepts]
  (when-ok [_ (check-code-system-content code-system)]
    (into
     []
     ((if active-only active-concept-xf concept-xf) code-system)
     value-set-concepts)))

(defmethod c/expand-filter :default
  [_ {:keys [url] :as code-system} filter]
  (when-ok [_ (check-code-system-content code-system)
            concepts (filter/filter-concepts filter code-system)]
    (into
     #{}
     (map
      (fn [{:keys [code display]}]
        {:system url :code code :display display}))
     concepts)))

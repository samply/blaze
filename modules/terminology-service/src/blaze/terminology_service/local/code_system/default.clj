(ns blaze.terminology-service.local.code-system.default
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.util :as u]
   [blaze.terminology-service.local.filter.core :as core]
   [blaze.terminology-service.local.filter.exists]
   [blaze.terminology-service.local.filter.is-a]
   [blaze.terminology-service.local.priority :as priority]
   [cognitect.anomalies :as anom]))

(defmethod c/find :default
  [{:keys [db]} url & [version]]
  (do-sync [code-systems (d/pull-many db (d/type-query db "CodeSystem" (cond-> [["url" url]] version (conj ["version" version]))))]
    (or (first (priority/sort-by-priority code-systems))
        (ba/not-found
         (if version
           (format "The code system with URL `%s` and version `%s` was not found." url version)
           (format "The code system with URL `%s` was not found." url))))))

(defmethod c/enhance :default
  [_ code-system]
  code-system)

(defn- concept-pred [code]
  (fn [concept]
    (when (= code (type/value (:code concept)))
      concept)))

(defn- not-found-msg [{:keys [url]} code]
  (if url
    (format "The provided code `%s` was not found in the code system with URL `%s`." code (type/value url))
    (format "The provided code `%s` was not found in the provided code system." code)))

(defn- find-concept
  [{concepts :concept :as code-system} code]
  (or (some (concept-pred code) concepts)
      (ba/not-found (not-found-msg code-system code))))

(defn- check-complete [{:keys [content] :as code-system}]
  (when-not (= "complete" (type/value content))
    (ba/incorrect (format "Can't use the code system with URL `%s` because it is not complete. It's content is `%s`." (type/value (:url code-system)) (type/value content)))))

(defmethod c/validate-code :default
  [{:keys [url] :as code-system} {:keys [request]}]
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
  (format "Can't expand the code system with URL `%s` because it is not complete. It's content is `%s`."
          url (type/value content)))

(defn- code-system-not-complete-anom [code-system]
  (ba/unsupported (code-system-not-complete-msg code-system) :http/status 409))

(defn- check-code-system-content [{:keys [content] :as code-system}]
  (if (= "complete" (type/value content))
    code-system
    (code-system-not-complete-anom code-system)))

(defmethod c/expand-complete :default
  [code-system]
  (when-ok [{:keys [url] concepts :concept} (check-code-system-content code-system)]
    (mapv
     (fn [{:keys [code display]}]
       (cond-> {:system url :code code}
         display (assoc :display display)))
     concepts)))

(defn- priority [{:keys [op]}]
  (case (type/value op)
    "is-a" 0
    1))

(defn- order-filters [filters]
  (sort-by priority filters))

(defn- filter-concepts [filters concepts]
  (reduce
   (fn [concepts filter]
     (let [res (core/filter-concepts filter concepts)]
       (cond-> res (ba/anomaly? res) reduced)))
   concepts
   filters))

(defmethod c/expand-filter :default
  [code-system filters]
  (when-ok [{:keys [url] concepts :concept} (check-code-system-content code-system)
            concepts (filter-concepts (order-filters filters) concepts)]
    (into
     []
     (map
      (fn [{:keys [code display]}]
        {:system url :code code :display display}))
     concepts)))

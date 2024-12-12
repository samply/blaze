(ns blaze.terminology-service.local.code-system.sct
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.sct.context :as context]
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]
   [blaze.terminology-service.local.code-system.sct.util :as sct-u]
   [blaze.terminology-service.local.code-system.util :as u]
   [blaze.terminology-service.local.priority :as priority]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]))

(defn- handles [db version]
  (cond
    (nil? version)
    (d/pull-many db (d/type-query db "CodeSystem" [["url" "http://snomed.info/sct"]]))

    (re-matches sct-u/module-only-version-pattern version)
    (do-sync [code-systems (d/pull-many db (d/type-query db "CodeSystem" [["url" "http://snomed.info/sct"]]))]
      (filterv
       #(str/starts-with? (type/value (:version %)) version)
       code-systems))

    :else
    (d/pull-many db (d/type-query db "CodeSystem" [["url" "http://snomed.info/sct"] ["version" version]]))))

(defn- assoc-context [{:keys [version] :as code-system} context]
  (when-ok [[module-id version] (sct-u/module-version (type/value version))]
    (assoc code-system :sct/context context :sct/module-id module-id
           :sct/version version)))

(defmethod c/find :sct
  [{:keys [db] :sct/keys [context]} url & [version]]
  (do-sync [code-systems (handles db version)]
    (or (some-> (first (priority/sort-by-priority code-systems))
                (assoc-context context))
        (ba/not-found
         (if version
           (format "The code system with URL `%s` and version `%s` was not found." url version)
           (format "The code system with URL `%s` was not found." url))))))

(defmethod c/enhance :sct
  [{:sct/keys [context]} code-system]
  (assoc-context code-system context))

(defn- not-found-msg [code]
  (format "The provided code `%s` was not found in the code system with URL `http://snomed.info/sct`." code))

(defn- find-concept
  [{{:keys [concept-index]} :sct/context :sct/keys [module-id version]} code]
  (if (some->> (parse-sctid code) (context/find-concept concept-index module-id version))
    {:code (type/code code)}
    (ba/not-found (not-found-msg code))))

(defmethod c/validate-code :sct
  [{:keys [url] :as code-system} {:keys [request]}]
  (if-ok [code (u/extract-code request (type/value url))
          {:keys [code]} (find-concept code-system code)]
    {:fhir/type :fhir/Parameters
     :parameter
     [(u/parameter "result" #fhir/boolean true)
      (u/parameter "code" code)
      (u/parameter "system" #fhir/uri"http://snomed.info/sct")]}
    (fn [{::anom/keys [message]}]
      {:fhir/type :fhir/Parameters
       :parameter
       [(u/parameter "result" #fhir/boolean false)
        (u/parameter "message" (type/string message))]})))

(defn- priority [{:keys [op]}]
  (case (type/value op)
    "is-a" 0
    1))

(defn- order-filters [filters]
  (sort-by priority filters))

(defmulti filter-one
  {:arglists '([code-system filter])}
  (fn [_ {:keys [op]}] (-> op type/value keyword)))

(defmethod filter-one :is-a
  [{{:keys [child-index]} :sct/context :sct/keys [module-id version]}
   {:keys [value]}]
  (context/transitive-neighbors child-index module-id version
                                (parse-sctid (type/value value))))

(defmethod filter-one :descendent-of
  [{{:keys [child-index]} :sct/context :sct/keys [module-id version]}
   {:keys [value]}]
  (let [concept-id (parse-sctid (type/value value))]
    (-> (context/transitive-neighbors child-index module-id version concept-id)
        (disj concept-id))))

(defn- filter-concepts [code-system filters]
  (reduce
   (fn [_ filter]
     (let [res (filter-one code-system filter)]
       (cond-> res (ba/anomaly? res) reduced)))
   nil
   filters))

(defmethod c/expand-filter :sct
  [{:sct/keys [version] {:keys [description-index]} :sct/context :as code-system}
   filters]
  (when-ok [concepts (filter-concepts code-system (order-filters filters))]
    (into
     []
     (map
      (fn [code]
        {:system #fhir/uri"http://snomed.info/sct"
         :code (type/code (str code))
         :display (type/string (context/find-description description-index code version))}))
     concepts)))

(defn build-context [release-path]
  (time (context/build release-path)))

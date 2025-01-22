(ns blaze.terminology-service.local.value-set
  (:refer-clojure :exclude [find])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local :as-alias local]
   [blaze.terminology-service.local.priority :as priority]))

(defn- find-in-tx-resources
  ([tx-resources url]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/ValueSet type)
        (when (= url (type/value (:url resource)))
          (ac/completed-future resource))))
    tx-resources))
  ([tx-resources url version]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/ValueSet type)
        (when (= url (type/value (:url resource)))
          (when (= version (type/value (:version resource)))
            (ac/completed-future resource)))))
    tx-resources)))

(defn- find-in-db
  ([db url]
   (do-sync [value-sets (d/pull-many db (d/type-query db "ValueSet" [["url" url]]))]
     (or (first (priority/sort-by-priority value-sets))
         (ba/not-found (format "The value set `%s` was not found." url)
                       ::local/category :value-set-not-found
                       :value-set/url url))))
  ([db url version]
   (do-sync [value-sets (d/pull-many db (d/type-query db "ValueSet" [["url" url] ["version" version]]))]
     (or (first (priority/sort-by-priority value-sets))
         (ba/not-found (format "The value set `%s|%s` was not found." url version))))))

(defn find
  "Returns a CompletableFuture that will complete with the first ValueSet
  resource with `url` and optional `version` in `db` according to priority or
  complete exceptionally in case of none found or errors."
  {:arglists '([context url] [context url version])}
  ([{:keys [db tx-resources]} url]
   (or (some-> tx-resources (find-in-tx-resources url))
       (find-in-db db url)))
  ([{:keys [db tx-resources]} url version]
   (or (some-> tx-resources (find-in-tx-resources url version))
       (find-in-db db url version))))

(defn- extension-filter [url]
  (filter #(= url (:url %))))

(defn extension-params
  {:arglists '([value-set])}
  [{{extensions :extension} :compose}]
  {:fhir/type :fhir/Parameters
   :parameter
   (into
    []
    (comp
     (extension-filter "http://hl7.org/fhir/tools/StructureDefinion/valueset-expansion-param")
     (map
      (fn [{extensions :extension}]
        (reduce
         (fn [param {:keys [url value]}]
           (condp = url
             "name" (assoc param :name (type/string (type/value value)))
             "value" (assoc param :value value)
             param))
         {:fhir/type :fhir.Parameters/parameter}
         extensions))))
    extensions)})

(defn display-language-param
  {:arglists '([value-set])}
  [{:keys [language]}]
  (when language
    {:display-languages [(type/value language)]}))

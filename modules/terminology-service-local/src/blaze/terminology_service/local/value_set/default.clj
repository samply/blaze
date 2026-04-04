(ns blaze.terminology-service.local.value-set.default
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.util :as fu]
   [blaze.terminology-service.local.value-set.core :as c]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]))

(defn- clauses [url version]
  (cond-> [["url" url]] version (conj ["version" version])))

(defn- value-set-query [db url version]
  (d/type-query db "ValueSet" (clauses url version)))

(defn- not-found-msg [url version]
  (if version
    (format "The value set `%s|%s` was not found." url version)
    (format "The value set `%s` was not found." url)))

(defmethod c/find :default
  [{:keys [db]} url & [version]]
  (-> (value-set-query db url version)
      (ac/then-compose
       (fn [handles]
         (do-sync [value-sets (d/pull-many db (vec handles))]
           (or (first (fu/sort-by-priority value-sets))
               (ba/not-found
                (not-found-msg url version)
                :fhir/issues
                [{:fhir.issues/code "not-found"
                  :fhir.issues/details (issue/value-set-not-found-details url)}])))))))

(ns blaze.terminology-service.local.value-set.default
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.terminology-service.local.priority :as priority]
   [blaze.terminology-service.local.value-set.core :as c]))

(defn- clauses [url version]
  (cond-> [["url" url]] version (conj ["version" version])))

(defn- not-found-msg [url version]
  (if version
    (format "The value set `%s|%s` was not found." url version)
    (format "The value set `%s` was not found." url)))

(defmethod c/find :default
  [{:keys [db]} url & [version]]
  (do-sync [value-sets (d/pull-many db (d/type-query db "ValueSet" (clauses url version)))]
    (or (first (priority/sort-by-priority value-sets))
        (ba/not-found (not-found-msg url version)))))

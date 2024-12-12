(ns blaze.terminology-service.local.value-set
  (:refer-clojure :exclude [find])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.terminology-service.local.priority :as priority]))

(defn find
  "Returns a CompletableFuture that will complete with the first ValueSet
  resource with `url` and optional `version` in `db` according to priority or
  complete exceptionally in case of none found or errors."
  ([db url]
   (do-sync [code-systems (d/pull-many db (d/type-query db "ValueSet" [["url" url]]))]
     (or (first (priority/sort-by-priority code-systems))
         (ba/not-found (format "The value set `%s` was not found." url)))))
  ([db url version]
   (do-sync [code-systems (d/pull-many db (d/type-query db "ValueSet" [["url" url] ["version" version]]))]
     (or (first (priority/sort-by-priority code-systems))
         (ba/not-found (format "The value set `%s` and version `%s` was not found." url version))))))

(ns blaze.terminology-service.local.code-system
  (:refer-clojure :exclude [find list])
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.default]
   [blaze.terminology-service.local.code-system.sct]
   [blaze.terminology-service.local.code-system.ucum]
   [blaze.terminology-service.local.priority :as priority]))

(defn list
  "Returns a CompletableFuture that will complete with an index of CodeSystem
  resources or complete exceptionally in case of errors.

  The index consists of a map of CodeSystem URL to a list of CodeSystem
  resources ordered by falling priority."
  [db]
  (do-sync [code-systems (d/pull-many db (d/type-list db "CodeSystem"))]
    (into
     {}
     (map
      (fn [[url code-systems]]
        [url (priority/sort-by-priority code-systems)]))
     (group-by (comp type/value :url) code-systems))))

(defn find
  "Returns a CompletableFuture that will complete with the first CodeSystem
  resource with `url` and optional `version` in `context` according to priority or
  complete exceptionally in case in case of none found of errors."
  {:arglists '([context url] [context url version])}
  [& args]
  (apply c/find args))

(defn enhance
  [context code-system]
  (c/enhance context code-system))

(defn validate-code [code-system context]
  (c/validate-code code-system context))

(defn expand-complete
  [code-system]
  (c/expand-complete code-system))

(defn expand-filter
  "Returns a list of concepts as expansion of `code-system` according to
  `filters`."
  [code-system filters]
  (c/expand-filter code-system filters))

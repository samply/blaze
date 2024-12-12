(ns blaze.terminology-service.local.code-system
  "Main code system functionality."
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
  resource with `url` and optional `version` in `context` according to priority
  or complete exceptionally in case of none found or errors."
  {:arglists '([context url] [context url version])}
  [& args]
  (apply c/find args))

(defn enhance
  "Adds additional data to `code-system`."
  [context code-system]
  (c/enhance context code-system))

(defn validate-code
  "Returns a Parameters resource that contains the response of the validation
  `request`."
  [code-system request]
  (c/validate-code code-system request))

(defn expand-complete
  "Returns a list of all concepts as expansion of `code-system`."
  [request code-system]
  (c/expand-complete request code-system))

(defn expand-concept
  "Returns a list of concepts as expansion of `code-system` according to the
  given `concepts`."
  [request code-system concepts]
  (c/expand-concept request code-system concepts))

(defn expand-filter
  "Returns a set of concepts as expansion of `code-system` according to
  `filter`."
  [request code-system filter]
  (c/expand-filter request code-system filter))

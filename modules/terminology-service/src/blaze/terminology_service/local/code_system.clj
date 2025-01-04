(ns blaze.terminology-service.local.code-system
  "Main code system functionality."
  (:refer-clojure :exclude [find list])
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.default]
   [blaze.terminology-service.local.code-system.sct]
   [blaze.terminology-service.local.code-system.ucum]
   [blaze.terminology-service.local.graph :as graph]
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

(defn- assoc-graph [{concepts :concept :as code-system}]
  (assoc code-system :default/graph (graph/build-graph concepts)))

(defn- find-in-tx-resources
  ([tx-resources url]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/CodeSystem type)
        (when (= url (type/value (:url resource)))
          (ac/completed-future (assoc-graph resource)))))
    tx-resources))
  ([tx-resources url version]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/CodeSystem type)
        (when (= url (type/value (:url resource)))
          (when (= version (type/value (:version resource)))
            (ac/completed-future (assoc-graph resource))))))
    tx-resources)))

(defn find
  "Returns a CompletableFuture that will complete with the first CodeSystem
  resource with `url` and optional `version` in `context` according to priority
  or complete exceptionally in case of none found or errors."
  {:arglists '([context url] [context url version])}
  ([{:keys [tx-resources] :as context} url]
   (or (some-> tx-resources (find-in-tx-resources url))
       (c/find context url)))
  ([{:keys [tx-resources] :as context} url version]
   (or (some-> tx-resources (find-in-tx-resources url version))
       (c/find context url version))))

(defn enhance
  "Adds additional data to `code-system`."
  [context code-system]
  (c/enhance context code-system))

(defn validate-code
  "Returns a Parameters resource that contains the response of the validation
  `params`."
  [code-system params]
  (c/validate-code code-system params))

(defn expand-complete
  "Returns a list of all concepts as expansion of `code-system`."
  [code-system params]
  (c/expand-complete code-system params))

(defn expand-concept
  "Returns a list of concepts as expansion of `code-system` according to the
  given `concepts`."
  [code-system concepts params]
  (c/expand-concept code-system concepts params))

(defn expand-filter
  "Returns a set of concepts as expansion of `code-system` according to
  `filter` or an anomaly in case of errors."
  [code-system filter params]
  (c/expand-filter code-system filter params))

(defn find-complete
  "Returns the concept according to `params` if it exists in `code-system`."
  [code-system params]
  (c/find-complete code-system params))

(defn find-filter
  "Returns the concept according to `params` if it exists in `code-system` and
  satisfies `filter` or an anomaly in case of errors."
  [code-system filter params]
  (c/find-filter code-system filter params))

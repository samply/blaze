(ns blaze.terminology-service.local.code-system
  "Main code system functionality."
  (:refer-clojure :exclude [find list])
  (:require
   [blaze.anomaly :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.fhir.util :as fu]
   [blaze.terminology-service.local.code-system :as-alias cs]
   [blaze.terminology-service.local.code-system.bcp-13]
   [blaze.terminology-service.local.code-system.bcp-47]
   [blaze.terminology-service.local.code-system.core :as c]
   [blaze.terminology-service.local.code-system.default]
   [blaze.terminology-service.local.code-system.sct]
   [blaze.terminology-service.local.code-system.ucum]
   [blaze.terminology-service.local.graph :as graph]
   [blaze.terminology-service.local.validate-code :as vc]
   [blaze.terminology-service.local.value-set.validate-code.issue :as issue]))

(defn- filter-usable
  "Keeps code systems with complete or fragment content and the special ones."
  [code-systems]
  (filterv
   (fn [{{url :value} :url {content :value} :content}]
     (or (#{"complete" "fragment"} content)
         (#{"urn:ietf:bcp:13"
            "urn:ietf:bcp:47"
            "http://loinc.org"
            "http://snomed.info/sct"
            "http://unitsofmeasure.org"}
          url)))
   code-systems))

(defn- pull-code-systems [db]
  (d/pull-many db (vec (d/type-list db "CodeSystem")) {:variant :summary}))

(defn list
  "Returns a CompletableFuture that will complete with an index of CodeSystem
  resources or complete exceptionally in case of errors.

  The index consists of a map of CodeSystem URL to a list of CodeSystem
  resources ordered by falling priority."
  [db]
  (do-sync [code-systems (pull-code-systems db)]
    (into
     {}
     (map
      (fn [[url code-systems]]
        [url (fu/sort-by-priority code-systems)]))
     (group-by (comp :value :url) (filter-usable code-systems)))))

(defn- assoc-graph [{concepts :concept :as code-system}]
  (assoc code-system :default/graph (graph/build-graph concepts)))

(defn- find-in-tx-resources
  ([{:keys [tx-resources] ::cs/keys [required-content]
     :or {required-content #{"complete" "fragment"}}} url]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/CodeSystem type)
        (when (= url (:value (:url resource)))
          (when (required-content (:value (:content resource)))
            (ac/completed-future (assoc-graph resource))))))
    tx-resources))
  ([{:keys [tx-resources] ::cs/keys [required-content]
     :or {required-content #{"complete" "fragment"}}} url version]
   (some
    (fn [{:fhir/keys [type] :as resource}]
      (when (identical? :fhir/CodeSystem type)
        (when (= url (:value (:url resource)))
          (when (= version (:value (:version resource)))
            (when (required-content (:value (:content resource)))
              (ac/completed-future (assoc-graph resource)))))))
    tx-resources)))

(defn find
  "Returns a CompletableFuture that will complete with the first CodeSystem
  resource with `url` and optional `version` in `context` according to priority
  or complete exceptionally in case of none found or errors."
  {:arglists '([context url] [context url version])}
  ([context url]
   (or (find-in-tx-resources context url)
       (c/find context url)))
  ([context url version]
   (or (find-in-tx-resources context url version)
       (c/find context url version))))

(defn enhance
  "Adds additional data to `code-system`."
  [context code-system]
  (c/enhance context code-system))

(defn- find-code [code-system {:keys [clause] :as params}]
  (or (c/find-complete code-system params)
      (vc/issue-anom-clause clause (issue/invalid-code clause))))

(defn- validate-code** [code-system {{:keys [display]} :clause :as params}]
  (when-ok [concept (find-code code-system params)]
    (cond->> concept
      display (vc/check-display {} params))))

(defn validate-code*
  [code-system params]
  (if-ok [concept (validate-code** code-system params)]
    (vc/parameters-from-concept concept params)
    #(vc/fail-parameters-from-anom % params)))

(defn- assoc-system-info [clause {{url :value} :url {version :value} :version}]
  (cond-> clause url (assoc :system url) version (assoc :version version)))

(defn validate-code
  "Returns a Parameters resource that contains the response of the validation
  `params`."
  [code-system params]
  (validate-code* code-system (update params :clause assoc-system-info code-system)))

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

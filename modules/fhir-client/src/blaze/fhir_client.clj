(ns blaze.fhir-client
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.flow :as flow]
    [blaze.fhir-client.impl :as impl]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Flow$Publisher])
  (:refer-clojure :exclude [read spit update]))


(set! *warn-on-reflection* true)


(defn metadata
  "Returns a CompletableFuture that completes with the CapabilityStatement in
  case of success or completes exceptionally with an anomaly in case of an
  error."
  [base-uri & [opts]]
  (impl/fetch (str base-uri "/metadata") opts))


(defn read
  "Returns a CompletableFuture that completes with the resource with `type` and
  `id` in case of success or completes exceptionally with an anomaly in case of
  an error."
  [base-uri type id & [opts]]
  (impl/fetch (str base-uri "/" type "/" id) opts))


(defn update
  "Returns a CompletableFuture that completes with `resource` updated."
  {:arglists '([base-uri resource & [opts]])}
  [base-uri {:fhir/keys [type] :keys [id] :as resource} & [opts]]
  (impl/update (str base-uri "/" (name type) "/" id) resource opts))


(defn- execute-type-get-msg [type name {:keys [query-params]}]
  (format "Execute $%s on type %s with params %s" name type query-params))


(defn execute-type-get
  "Executes the operation with `name` on the type-level endpoint with `type`
  using GET.

  Params to the operation can be given in :query-params in `opts`.

  Returns a CompletableFuture that completes with either a Parameters resource
  or a resource of the type of the single out parameter named `return`."
  [base-uri type name & [opts]]
  (log/trace (execute-type-get-msg type name opts))
  (impl/fetch (apply str base-uri "/" type "/$" name) opts))


(defn search-type-publisher
  "Returns a Publisher that produces a Bundle per page of resources with `type`.

  Use `resource-processor` to transform the pages to individual resources. Use
  `search-type` if you simply want to fetch all resources."
  [base-uri type & [opts]]
  (reify Flow$Publisher
    (subscribe [_ subscriber]
      (->> (impl/paging-subscription subscriber (str base-uri "/" type) opts)
           (flow/on-subscribe! subscriber)))))


(defn resource-processor
  "Returns a Processor that produces resources from Bundle entries produced."
  []
  (flow/mapcat #(map :resource (:entry %))))


(defn search-type
  "Returns a CompletableFuture that completes with all resource of `type` in
  case of success or completes exceptionally with an anomaly in case of an
  error."
  [base-uri type & [opts]]
  (let [src (search-type-publisher base-uri type opts)
        pro (resource-processor)
        dst (flow/collect pro)]
    (flow/subscribe! src pro)
    dst))


(defn search-system-publisher
  "Returns a Publisher that produces a Bundle per page of resources.

  Use `resource-processor` to transform the pages to individual resources. Use
  `search-system` if you simply want to fetch all resources."
  [base-uri & [opts]]
  (reify Flow$Publisher
    (subscribe [_ subscriber]
      (->> (impl/paging-subscription subscriber base-uri opts)
           (flow/on-subscribe! subscriber)))))


(defn search-system
  "Returns a CompletableFuture that completes with all resource in case of
  success or completes exceptionally with an anomaly in case of an error."
  [base-uri & [opts]]
  (let [src (search-system-publisher base-uri opts)
        pro (resource-processor)
        dst (flow/collect pro)]
    (flow/subscribe! src pro)
    dst))


(defn spit
  "Returns a CompletableFuture that completes with a vector of all filenames
  written of all resources the `publisher` produces."
  [dir publisher]
  (let [future (ac/future)]
    (flow/subscribe! publisher (impl/spitter dir future))
    future))

(ns blaze.fhir-client
  (:refer-clojure :exclude [read spit str update])
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.flow :as flow]
   [blaze.fhir-client.impl :as impl]
   [blaze.util :refer [str]]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Flow$Publisher]))

(set! *warn-on-reflection* true)

(defn metadata
  "Returns a CompletableFuture that will complete with the CapabilityStatement
  in case of success or will complete exceptionally with an anomaly in case of
  an error."
  [base-uri & [opts]]
  (impl/fetch (str base-uri "/metadata") opts))

(defn read
  "Returns a CompletableFuture that will complete with the resource with `type`
  and `id` in case of success or will complete exceptionally with an anomaly in
  case of an error."
  [base-uri type id & [opts]]
  (impl/fetch (str base-uri "/" type "/" id) opts))

(defn create
  "Returns a CompletableFuture that will complete with `resource` created."
  {:arglists '([base-uri resource & [opts]])}
  [base-uri {:fhir/keys [type] :as resource} & [opts]]
  (impl/create (str base-uri "/" (name type)) resource opts))

(defn update
  "Returns a CompletableFuture that will complete with `resource` updated."
  {:arglists '([base-uri resource & [opts]])}
  [base-uri {:fhir/keys [type] :keys [id] :as resource} & [opts]]
  (impl/update (str base-uri "/" (name type) "/" id) resource opts))

(defn delete
  "Returns a CompletableFuture that will complete with resource of `type` and
  `id` deleted."
  [base-uri type id & [opts]]
  (impl/delete (str base-uri "/" type "/" id) opts))

(defn delete-history
  "Returns a CompletableFuture that will complete with resource of `type` and
  `id` deleted."
  {:arglists '([base-uri type id & [opts]])}
  [base-uri type id & [opts]]
  (impl/delete (str base-uri "/" type "/" id "/_history") opts))

(defn transact
  "Returns a CompletableFuture that will complete with `bundle` transacted."
  {:arglists '([base-uri bundle & [opts]])}
  [base-uri bundle & [opts]]
  (impl/post base-uri bundle opts))

(defn- execute-type-get-msg [type name {:keys [query-params]}]
  (format "Execute $%s on type %s with params %s" name type query-params))

(defn execute-type-get
  "Executes the operation with `name` on the type-level endpoint with `type`
  using GET.

  Params to the operation can be given in :query-params in `opts`.

  Returns a CompletableFuture that will complete with either a Parameters
  resource or a resource of the type of the single out parameter named
  `return`."
  [base-uri type name & [opts]]
  (log/trace (execute-type-get-msg type name opts))
  (impl/fetch (str base-uri "/" type "/$" name) opts))

(defn- execute-type-post-msg [type name params]
  (format "Execute $%s on type %s with params %s" name type params))

(defn execute-type-post
  "Executes the operation with `name` on the type-level endpoint with `type`
  using POST.

  Returns a CompletableFuture that will complete with either a Parameters
  resource or a resource of the type of the single out parameter named
  `return`."
  [base-uri type name params & [opts]]
  (log/trace (execute-type-post-msg type name params))
  (impl/post (str base-uri "/" type "/$" name) params opts))

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
  "Returns a CompletableFuture that will complete with all resources of `type`
  in case of success or will complete exceptionally with an anomaly in case of
  an error."
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
  "Returns a CompletableFuture that will complete with all resource in case of
  success or will complete exceptionally with an anomaly in case of an error."
  [base-uri & [opts]]
  (let [src (search-system-publisher base-uri opts)
        pro (resource-processor)
        dst (flow/collect pro)]
    (flow/subscribe! src pro)
    dst))

(defn history-instance-publisher
  "Returns a Publisher that produces a Bundle per page of versions of resource
  with `type` and `id`.

  Use `resource-processor` to transform the pages to individual resources. Use
  `history-instance` if you simply want to fetch all resources."
  [base-uri type id & [opts]]
  (reify Flow$Publisher
    (subscribe [_ subscriber]
      (->> (impl/paging-subscription subscriber (str base-uri "/" type "/" id "/_history") opts)
           (flow/on-subscribe! subscriber)))))

(defn history-instance
  "Returns a CompletableFuture that will complete with all versions of resource
  with `type` and `id` in case of success or will complete exceptionally with an
  anomaly in case of an error."
  [base-uri type id & [opts]]
  (let [src (history-instance-publisher base-uri type id opts)
        pro (resource-processor)
        dst (flow/collect pro)]
    (flow/subscribe! src pro)
    dst))

(defn spit
  "Returns a CompletableFuture that will complete with a vector of all filenames
  written of all resources the `publisher` produces."
  [writing-context dir publisher]
  (let [future (ac/future)]
    (flow/subscribe! publisher (impl/spitter writing-context dir future))
    future))

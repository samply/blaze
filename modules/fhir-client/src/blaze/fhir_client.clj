(ns blaze.fhir-client
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.flow :as flow]
    [blaze.fhir-client.impl :as impl])
  (:import
    [java.util.concurrent Flow$Publisher])
  (:refer-clojure :exclude [read spit update]))


(set! *warn-on-reflection* true)


(defn client
  ([base-uri]
   (client impl/default-http-client base-uri))
  ([http-client base-uri]
   {:http-client http-client
    :base-uri base-uri}))


(defn metadata
  "Returns a CompletableFuture that completes with the CapabilityStatement in
  case of success or completes exceptionally with an anomaly in case of an error."
  {:arglists '([client])}
  [{:keys [base-uri http-client]}]
  (impl/fetch http-client (str base-uri "/metadata")))


(defn read
  "Returns a CompletableFuture that completes with the resource with `type` and
  `id` in case of success or completes exceptionally with an anomaly in case of
  an error."
  {:arglists '([client type id])}
  [{:keys [base-uri http-client]} type id]
  (impl/fetch http-client (str base-uri "/" type "/" id)))


(defn update
  "Returns a CompletableFuture that completes with `resource` updated."
  [client resource]
  (impl/update client resource))


(defn- query-str [params]
  (interpose "&" (map (fn [[k v]] (str k "=" v)) params)))


(defn- search-type-uri [base-uri type params]
  (if (seq params)
    (apply str base-uri "/" type "?" (query-str params))
    (str base-uri "/" type)))


(defn search-type-publisher
  "Returns a Publisher that produces a Bundle per page of resources with `type`.

  Use `resource-processor` to transform the pages to individual resources. Use
  `search-type` if you simply want to fetch all resources."
  {:arglists '([client type params])}
  [{:keys [base-uri http-client]} type params]
  (reify Flow$Publisher
    (subscribe [_ subscriber]
      (->> (search-type-uri base-uri type params)
           (impl/search-type-subscription subscriber http-client)
           (.onSubscribe subscriber)))))


(defn resource-processor
  "Returns a Processor that produces resources from Bundle entries produced."
  []
  (flow/mapcat #(map :resource (:entry %))))


(defn search-type
  "Returns a CompletableFuture that completes with all resource of `type` in
  case of success or completes exceptionally with an anomaly in case of an
  error."
  ([client type]
   (search-type client type {}))
  ([client type params]
   (let [src (search-type-publisher client type params)
         pro (resource-processor)
         dst (flow/collect pro)]
     (.subscribe ^Flow$Publisher src pro)
     dst)))


(defn spit
  "Returns a CompletableFuture that completes with a vector of all filenames
  written of all resources the `publisher` produces."
  [dir publisher]
  (let [future (ac/future)]
    (.subscribe ^Flow$Publisher publisher (impl/spitter dir future))
    future))

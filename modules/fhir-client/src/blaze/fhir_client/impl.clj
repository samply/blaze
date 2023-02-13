(ns blaze.fhir-client.impl
  (:refer-clojure :exclude [update])
  (:require
    [blaze.anomaly :as ba :refer [when-ok]]
    [blaze.async.comp :as ac]
    [blaze.async.flow :as flow]
    [blaze.byte-buffer :as bb]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [hato.client :as hc]
    [hato.middleware :as hm]
    [taoensso.timbre :as log])
  (:import
    [java.nio.channels SeekableByteChannel]
    [java.nio.file Path Files StandardOpenOption]
    [java.util.concurrent Flow$Subscriber Flow$Subscription]))


(set! *warn-on-reflection* true)


(defn- json? [content-type]
  (some-> content-type name #{"fhir+json" "json"}))


(defn- category [status]
  (cond
    (= 404 status) ::anom/not-found
    (#{409 412} status) ::anom/conflict
    (#{401 403} status) ::anom/forbidden
    (<= 400 status 499) ::anom/incorrect
    (= 503 status) ::anom/unavailable
    (= 504 status) ::anom/busy
    :else ::anom/fault))


(defn- anomaly* [response]
  (if-let [status (:status response)]
    {::anom/category (category status)
     ::anom/message (format "Unexpected response status %d." status)}
    {::anom/category ::anom/fault
     ::anom/message "Missing response status code."}))


(defn- anomaly [e]
  (let [data (ex-data e)]
    (if (ba/anomaly? data)
      (assoc data ::anom/category ::anom/fault)
      (cond-> (anomaly* data)
        (identical? :fhir/OperationOutcome (-> data :body fhir-spec/fhir-type))
        (assoc :fhir/issues (-> data :body :issue))))))


(defn- handle-error [e]
  (ba/throw-when (anomaly e)))


(defmethod hm/coerce-response-body :fhir
  [_ {:keys [body content-type] :as resp}]
  (let [charset (or (-> resp :content-type-params :charset) "UTF-8")]
    (if (json? content-type)
      (ba/throw-when
        (with-open [r (io/reader body :encoding charset)]
          (when-ok [x (fhir-spec/parse-json r)
                    resource (fhir-spec/conform-json x)]
            (assoc resp :body resource))))
      resp)))


(def ^:private cache-control
  "Especially important to HAPI

  See: https://hapifhir.io/hapi-fhir/docs/server_jpa/configuration.html#search-result-caching"
  {"cache-control" "no-cache"})


(defn fetch
  "Returns a CompletableFuture that completes with the resource at `uri` in case
  of success or completes exceptionally with an anomaly in case of an error."
  [uri opts]
  (log/trace "Fetch" uri)
  (hc/get
    uri
    (merge
      {:accept :fhir+json
       :headers cache-control
       :as :fhir
       :async? true}
      opts)
    :body
    handle-error))


(defn- etag [resource]
  (when-let [version-id (-> resource :meta :versionId)]
    (str "W/\"" version-id "\"")))


(defn- generate-body [resource]
  (fhir-spec/unform-json resource))


(defn- if-match [etag]
  {"if-match" etag})


(defn update [uri resource opts]
  (log/trace "Update" uri)
  (hc/put
    uri
    (merge
      {:accept :fhir+json
       :content-type :fhir+json
       :headers (some-> (etag resource) if-match)
       :body (generate-body resource)
       :as :fhir
       :async? true}
      opts)
    :body
    handle-error))


(defn post [uri resource opts]
  (log/trace "Create" uri generate-body)
  (hc/post
    uri
    (merge
      {:accept :fhir+json
       :content-type :fhir+json
       :body (generate-body resource)
       :as :fhir
       :async? true}
      opts)
    :body
    handle-error))


(defn- next-url [page]
  (type/value (:url (first (filter (comp #{"next"} :relation) (:link page))))))


(deftype PagingSubscription
  [^Flow$Subscriber subscriber volatile-uri opts]
  Flow$Subscription
  (request [_ _]
    (when-let [uri @volatile-uri]
      (-> (fetch uri opts)
          (ac/when-complete
            (fn [page e]
              (if e
                (.onError subscriber e)
                (if-let [next (next-url page)]
                  (do (log/trace "next uri" next)
                      (vreset! volatile-uri next)
                      (.onNext subscriber page))
                  (do (vreset! volatile-uri nil)
                      (.onNext subscriber page)
                      (.onComplete subscriber)))))))))
  (cancel [_]))


(defn paging-subscription [subscriber uri opts]
  (->PagingSubscription subscriber (volatile! uri) opts))


(defn- byte-channel ^SeekableByteChannel [file & open-options]
  (Files/newByteChannel file (into-array open-options)))


(defn- new-file-byte-channel ^SeekableByteChannel [file]
  (byte-channel file StandardOpenOption/CREATE_NEW StandardOpenOption/WRITE))


(deftype Spitter
  [dir filename-fn filenames future ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (flow/request! s 1))
  (onNext [_ x]
    (flow/request! subscription 1)
    (let [file (.resolve ^Path dir ^String (filename-fn x))]
      (swap! filenames conj (.toAbsolutePath file))
      (with-open [bc (new-file-byte-channel file)]
        (.write bc (bb/wrap (fhir-spec/unform-json x))))))
  (onError [_ e]
    (flow/cancel! subscription)
    (ac/complete-exceptionally! future e))
  (onComplete [_]
    (ac/complete! future @filenames)))


(defn- type-id-filename-fn [{:fhir/keys [type] :keys [id]}]
  (str (name type) "-" id ".json"))


(defn spitter
  ([dir future]
   (spitter dir type-id-filename-fn future))
  ([dir filename-fn future]
   (->Spitter dir filename-fn (atom []) future nil)))

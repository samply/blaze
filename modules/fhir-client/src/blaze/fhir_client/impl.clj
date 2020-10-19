(ns blaze.fhir-client.impl
  (:require
    [blaze.anomaly :refer [throw-anom ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log])
  (:import
    [java.io BufferedWriter]
    [java.net URI]
    [java.net.http HttpClient HttpRequest HttpResponse$BodyHandler
                   HttpResponse$ResponseInfo
                   HttpResponse$BodySubscribers HttpResponse HttpRequest$BodyPublishers]
    [java.nio.charset StandardCharsets]
    [java.nio.file Path Files StandardOpenOption]
    [java.util.concurrent Flow$Subscriber Flow$Subscription]
    [java.util.function Function])
  (:refer-clojure :exclude [update]))


(set! *warn-on-reflection* true)


(defn- parse-body [body]
  (with-open [reader (io/reader body)]
    (binding [*use-bigdecimals?* true]
      (try
        (json/parse-stream reader keyword)
        (catch Exception e
          (throw-anom ::anom/fault (ex-message e)))))))


(defn- json-subscriber []
  (-> (HttpResponse$BodySubscribers/ofInputStream)
      (HttpResponse$BodySubscribers/mapping
        (reify Function
          (apply [_ body]
            (fhir-spec/conform-json (parse-body body)))))))


(defn- header [^HttpResponse$ResponseInfo response-info name]
  (let [value (.firstValue (.headers response-info) name)]
    (when (.isPresent value)
      (.get value))))


(defn- json? [content-type]
  (or (str/starts-with? content-type "application/fhir+json")
      (str/starts-with? content-type "application/json")))


(def ^:private body-handler
  (reify HttpResponse$BodyHandler
    (apply [_ response-info]
      (if (some-> (header response-info "content-type") json?)
        (json-subscriber)
        (HttpResponse$BodySubscribers/discarding)))))


(defn- category [status]
  (cond
    (= 404 status) ::anom/not-found
    (#{409 412} status) ::anom/conflict
    (#{401 403} status) ::anom/forbidden
    (<= 400 status 499) ::anom/incorrect
    (= 503 status) ::anom/unavailable
    (= 504 status) ::anom/busy
    :else ::anom/fault))


(defn- anomaly [^HttpResponse response]
  (cond->
    {::anom/category (category (.statusCode response))
     ::anom/message (format "Unexpected response status %d." (.statusCode response))}
    (= :fhir/OperationOutcome (fhir-spec/fhir-type (.body response)))
    (assoc :fhir/issues (:issue (.body response)))))


(defn- to-body [^HttpResponse response]
  (if (< (.statusCode response) 400)
    (.body response)
    (throw (ex-anom (anomaly response)))))


(def default-http-client
  (-> (HttpClient/newBuilder)
      (.build)))


(defn fetch
  "Returns a CompletableFuture that completes with the resource at `uri` in case
  of success or completes exceptionally with an anomaly in case of an error."
  [http-client uri]
  (let [request (-> (HttpRequest/newBuilder)
                    (.uri (URI/create uri))
                    (.header "Accept" "application/fhir+json")
                    ;; Especially important with HAPI
                    ;; https://hapifhir.io/hapi-fhir/docs/server_jpa/configuration.html#search-result-caching
                    (.header "Cache-Control" "no-cache")
                    (.build))]
    (-> (.sendAsync ^HttpClient http-client request body-handler)
        (ac/then-apply to-body))))


(defn- etag [resource]
  (str "W/\"" (-> resource :meta :versionId) "\""))


(defn- body-publisher [resource]
  (HttpRequest$BodyPublishers/ofString
    (json/generate-string (fhir-spec/unform-json resource) {:key-fn name})))


(defn update
  [{:keys [base-uri http-client]} {:fhir/keys [type] :keys [id] :as resource}]
  (log/trace "update" (str base-uri "/" (name type) "/" id))
  (let [request (-> (HttpRequest/newBuilder)
                    (.uri (URI/create (str base-uri "/" (name type) "/" id)))
                    (.header "Content-Type" "application/fhir+json")
                    (.header "Accept" "application/fhir+json")
                    (.header "If-Match" (etag resource))
                    (.PUT (body-publisher resource))
                    (.build))]
    (-> (.sendAsync ^HttpClient http-client request body-handler)
        (ac/then-apply to-body))))


(defn- next-url [page]
  (type/value (:url (first (filter (comp #{"next"} :relation) (:link page))))))


(deftype SearchTypeSubscription
  [^Flow$Subscriber subscriber http-client volatile-uri]
  Flow$Subscription
  (request [_ _]
    (when-let [uri @volatile-uri]
      (log/trace "fetch" uri)
      (-> (fetch http-client uri)
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


(defn search-type-subscription [subscriber http-client uri]
  (->SearchTypeSubscription subscriber http-client (volatile! uri)))


(defn- writer ^BufferedWriter [file & open-options]
  (Files/newBufferedWriter file StandardCharsets/UTF_8 (into-array open-options)))


(deftype Spitter
  [dir filename-fn filenames future ^:volatile-mutable subscription]
  Flow$Subscriber
  (onSubscribe [_ s]
    (set! subscription s)
    (.request ^Flow$Subscription s 1))
  (onNext [_ x]
    (.request ^Flow$Subscription subscription 1)
    (let [file (.resolve ^Path dir ^String (filename-fn x))]
      (swap! filenames conj (.toAbsolutePath file))
      (with-open [w (writer file StandardOpenOption/CREATE_NEW)]
        (json/generate-stream (fhir-spec/unform-json x) w))))
  (onError [_ e]
    (.cancel ^Flow$Subscription subscription)
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

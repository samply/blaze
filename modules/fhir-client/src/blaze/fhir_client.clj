(ns blaze.fhir-client
  (:require
    [aleph.http :as http]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [manifold.deferred :as d :refer [deferred?]]
    [manifold.stream :as stream :refer [stream?]]))



;; ---- Middleware ------------------------------------------------------------

(defn- parse-body [body]
  (with-open [reader (io/reader body)]
    (json/parse-stream reader keyword)))


(defn- wrap-parse-body [client]
  (fn [req]
    (d/let-flow' [{:keys [headers] :as resp} (client req)]
      (if (some-> (get headers "content-type")
                  (str/starts-with? "application/fhir+json"))
        (update resp :body parse-body)
        resp))))


(defn- status>anomaly
  [status]
  (cond
    (= 404 status) ::anom/not-found
    (= 409 status) ::anom/conflict
    (#{401 403} status) ::anom/forbidden
    (<= 400 status 499) ::anom/incorrect
    (= 503 status) ::anom/unavailable
    :else ::anom/fault))


(defn- wrap-error-handling
  "Returns an error deferred for each response with status code from 400
  onwards.

  The `ex-data` will include an ::anom/category, ::anom/message, the ::request
  and the ::response."
  [client]
  (fn [req]
    (d/let-flow' [{:keys [status] :as resp} (client req)]
      (if (< status 300)
        resp
        (d/error-deferred
          {::anom/category (status>anomaly status)
           ::anom/message (format "Unexpected response status %d." status)
           ::request req
           ::response resp})))))


(defn- wrap-return-body
  [client]
  (fn [req]
    (d/let-flow' [{:keys [body]} (client req)]
      body)))


(defn- wrap-accept
  [req]
  (assoc-in req [:headers "accept"] "application/fhir+json"))


(defn- middleware
  "The middleware used for requests. It includes parsing the body to XML, error
  handling and accept header setting."
  [client]
  (fn [req]
    ((-> client
         wrap-parse-body
         wrap-error-handling
         wrap-return-body)
      (-> (assoc req :throw-exceptions false)
          wrap-accept))))



;; ---- Fetch -----------------------------------------------------------------

(defn- assoc-middleware
  [{existing-mw :middleware :or {existing-mw identity} :as opts}]
  (assoc opts :middleware (comp existing-mw middleware)))


(s/fdef fetch
  :args (s/cat :uri string? :opts map?)
  :ret deferred?)

(defn fetch
  "Fetches the resource at `uri` using `opts` which go to the Aleph HTTP client.

  Returns either a manifold deferred with the parsed body as map with keyword
  keys or an error deferred with an anomaly with includes the ::request and the
  ::response.

  Custom :middleware can be put into `opts` to handle things like authentication
  and caching. Also middleware from :pool is still used. The whole Aleph API can
  still be used though `opts`."
  [uri opts]
  (http/get uri (assoc-middleware opts)))


(defn fetch-list*
  "Fills pages stream as side-effect. Returns nothing of interest."
  [pages uri opts]
  (-> (fetch uri opts)
      (d/catch'
        (fn [e]
          ;; fetch error
          (stream/put! pages e)
          (stream/close! pages)))
      (d/chain'
        (fn [page]
          (d/let-flow' [put-success? (stream/put! pages page)]
            (when put-success?
              (if-let [next (:url (first (filter (comp #{"next"} :relation) (:link page))))]
                (fetch-list* pages next opts)
                (stream/close! pages))))))
      (d/catch'
        (fn [_]
          ;; internal page handling error
          (stream/close! pages)))))


(s/fdef fetch-list
  :args (s/cat :uri string? :opts map?)
  :ret stream?)

(defn fetch-list
  "Fetches the list resource at `uri` using `opts` which go to the Aleph HTTP
  client.

  Returns a manifold stream with each page as map with keyword keys. The last
  element in the stream can be an error with an anomaly with includes the
  ::request and the ::response. The stream is closed when all pages are fetched
  or an error occurred.

  Custom :middleware can be put into `opts` to handle things like authentication
  and caching. Also middleware from :pool is still used. The whole Aleph API can
  still be used though `opts`."
  [uri opts]
  (let [pages (stream/stream)]
    (fetch-list* pages uri opts)
    pages))


(s/fdef unroll-pages
  :args (s/cat :pages stream?)
  :ret stream?)

(defn unroll-pages
  "Takes a manifold stream of `pages` and returns a stream of the individual
  resources.

  Typical use is with `fetch-list` in order to get a stream of list items
  instead of a stream of pages."
  [pages]
  (stream/mapcat
    (fn [page]
      (if (instance? Throwable page)
        [page]
        (map :resource (:entry page))))
    pages))

(comment
  @(fetch "http://hapi.fhir.org/baseR4/Observation?_count=500" {})
  @(stream/reduce conj [] (unroll-pages (fetch-list "http://hapi.fhir.org/baseR4/Observation?_count=500" {})))
  )

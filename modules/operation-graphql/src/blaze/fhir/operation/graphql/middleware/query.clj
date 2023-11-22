(ns blaze.fhir.operation.graphql.middleware.query
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [jsonista.core :as j]
   [ring.util.request :as request]))

(defn- query-request-graphql [{:keys [body] :as request}]
  (if body
    (assoc request :body {:query (slurp body)})
    (ba/incorrect "Missing HTTP body.")))

(def ^:private object-mapper
  (j/object-mapper
   {:decode-key-fn true}))

(defn- parse-json [body]
  (ba/try-all ::anom/incorrect (j/read-value body object-mapper)))

(defn- conform-json [json]
  (if (map? json)
    (select-keys json [:query])
    (ba/incorrect
     "Expect a JSON object."
     :fhir/issue "structure"
     :fhir/operation-outcome "MSG_JSON_OBJECT")))

(defn- query-request-json [{:keys [body] :as request}]
  (if body
    (when-ok [x (parse-json body)
              query (conform-json x)]
      (assoc request :body query))
    (ba/incorrect "Missing HTTP body.")))

(defn- unsupported-media-type-msg [media-type]
  (format "Unsupported media type `%s` expect one of `application/graphql` or `application/json`."
          media-type))

(defn- query-request [request]
  (if-let [content-type (request/content-type request)]
    (cond
      (str/starts-with? content-type "application/graphql")
      (query-request-graphql request)

      (str/starts-with? content-type "application/json")
      (query-request-json request)

      :else
      (ba/incorrect (unsupported-media-type-msg content-type)
                    :http/status 415))
    (ba/incorrect "Content-Type header expected, but is missing.")))

(defn wrap-query
  "Middleware to slurp a GraphQL query from the body according the content-type
  header.

  Updates the :body key in the request map with a map consisting of at least a
  :query key. If the content-type is `application/graphql` the body string is
  the value of the query key. If the content-type is `application/json` the map
  is the parsed JSON body.

  Returns an OperationOutcome in the internal format, skipping the handler, with
  an appropriate error when a content-type other than `application/graphql` or
  `application/json` was specified.

  See also: https://graphql.org/learn/serving-over-http/"
  [handler]
  (fn [request]
    (if-ok [request (query-request request)]
      (handler request)
      ac/completed-future)))

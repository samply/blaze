(ns blaze.http-client.json
  "Strict JSON parsing of HTTP responses and other untrusted input.

  The mappers built here reject duplicate keys and limit documents by
  configurable stream read constraints."
  (:require
   [blaze.anomaly :as ba]
   [cognitect.anomalies :as anom]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.core JsonFactory JsonFactoryBuilder JsonProcessingException StreamReadConstraints StreamReadConstraints$Builder StreamReadFeature]
   [com.fasterxml.jackson.databind DeserializationFeature ObjectMapper]
   [java.io IOException InputStream]))

(set! *warn-on-reflection* true)

(defn- stream-read-constraints
  {:arglists '([constraints])}
  [{:keys [max-nesting-depth max-number-length max-name-length
           max-string-length max-document-length]}]
  (let [builder (StreamReadConstraints/builder)]
    (cond-> builder
      max-nesting-depth (.maxNestingDepth (int max-nesting-depth))
      max-number-length (.maxNumberLength (int max-number-length))
      max-name-length (.maxNameLength (int max-name-length))
      max-string-length (.maxStringLength (int max-string-length))
      max-document-length (.maxDocumentLength (long max-document-length)))
    (.build ^StreamReadConstraints$Builder builder)))

(defn- json-factory ^JsonFactory [constraints]
  (-> (JsonFactory/builder)
      (.streamReadConstraints (stream-read-constraints constraints))
      ^JsonFactoryBuilder (.enable StreamReadFeature/STRICT_DUPLICATE_DETECTION)
      (.build)))

(defn object-mapper
  "Returns an object mapper parsing JSON with keyword keys, rejecting duplicate
  keys and trailing tokens and applying `constraints`.

  Rejecting trailing tokens makes the parser read the input up to its end, so
  that `:max-document-length` counts every byte. Otherwise Jackson checks the
  document length only on refilling its input buffer, skipping the last one.

  The constraints are a map of the optional keys `:max-nesting-depth`,
  `:max-number-length`, `:max-name-length`, `:max-string-length` and
  `:max-document-length`. A missing key keeps the default of Jackson."
  ^ObjectMapper [constraints]
  (doto (j/object-mapper {:factory (json-factory constraints) :decode-key-fn true})
    (.enable DeserializationFeature/FAIL_ON_TRAILING_TOKENS)))

(defn parse-json
  "Parses the JSON document of `input-stream` with `mapper`.

  Returns the parsed data or an anomaly if the document is invalid, violates
  a constraint of `mapper` or can't be read. Closes `input-stream`."
  [mapper input-stream]
  (ba/try-one IOException ::anom/fault
    (ba/try-one JsonProcessingException ::anom/incorrect
      (j/read-value ^InputStream input-stream ^ObjectMapper mapper))))

(defn- successful? [status]
  (<= 200 status 299))

(defn- close-quietly
  "Closes `stream`, ignoring an I/O error, since the stream isn't used
  afterwards."
  [^InputStream stream]
  (ba/try-one IOException ::anom/fault (.close stream)))

(defn parse-response
  "Parses the body stream of the HTTP `response` with `mapper`, if its status
  is 2xx.

  Otherwise returns a fault anomaly naming the status. In every case the body
  stream is closed, so that the connection is released. Use it for responses
  requested with `:as :stream` and `:throw-exceptions false`."
  {:arglists '([mapper response])}
  [mapper {:keys [status body]}]
  (if (successful? status)
    (parse-json mapper body)
    (do (close-quietly body)
        (ba/fault (format "status: %d" status)))))

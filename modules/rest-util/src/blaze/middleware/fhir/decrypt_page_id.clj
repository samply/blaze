(ns blaze.middleware.fhir.decrypt-page-id
  "Contains functionality around encrypting/decrypting query parameters into
  page id's that are used in paging URL's.

  The middleware `wrap-decrypt-page-id` will decrypt the page id's into query
  parameters, while the `encrypt` function will encrypt query params into page
  id's."
  (:require
   [blaze.anomaly :as ba :refer [if-ok try-one]]
   [blaze.async.comp :as ac]
   [cheshire.core :as json]
   [cognitect.anomalies :as anom])
  (:import
   [com.google.crypto.tink Aead]
   [java.security GeneralSecurityException]
   [java.util Base64 Base64$Encoder]))

(set! *warn-on-reflection* true)

(defn- decrypt* [page-id-cipher input]
  (try-one GeneralSecurityException ::anom/incorrect
    (.decrypt ^Aead page-id-cipher input nil)))

(defn- b64-decode [page-id]
  (try-one IllegalArgumentException ::anom/incorrect
    (.decode (Base64/getUrlDecoder) ^String page-id)))

(defn- decrypt [page-id-cipher page-id]
  (if-ok [cipher-text (b64-decode page-id)
          clear-text (decrypt* page-id-cipher cipher-text)]
    (json/parse-cbor clear-text)
    (fn [_] (ba/not-found (format "Page with id `%s` not found." page-id)))))

(defn wrap-decrypt-page-id
  "Wraps a middleware round `handler` that decrypts the :page-id path param from
  the request using `page-id-cipher` and overrides :params and :query-params of
  the request with the result."
  {:arglists '([handler page-id-cipher])}
  [handler page-id-cipher]
  (fn [{{:keys [page-id]} :path-params :as request}]
    (if-ok [params (decrypt page-id-cipher page-id)]
      (handler (assoc request :params params :query-params params))
      ac/completed-future)))

(def ^:private ^Base64$Encoder b64-encoder
  (.withoutPadding (Base64/getUrlEncoder)))

(defn encrypt
  "Encrypts `query-params` using `page-id-cipher` into an base 64 encoded string
  that can be used as page id."
  {:arglists '([page-id-cipher query-params])}
  [page-id-cipher query-params]
  (let [clear-text (json/generate-cbor query-params)
        cipher-text (.encrypt ^Aead page-id-cipher clear-text nil)]
    (.encodeToString b64-encoder cipher-text)))

(ns blaze.middleware.params
  "Middleware to decode urlencoded parameters from the query string and the
  request body.

  Unlike `ring.middleware.params`, which silently drops parameters that aren't
  properly percent-encoded, requests with such parameters are rejected.

  The decoding is done by `wrap-params`, which has to run before the output
  middleware, because it needs the `_format` parameter. Rejecting is done by
  `wrap-invalid-params`, which has to run after the error middleware, so that
  the anomaly is converted into an error response."
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.async.comp :as ac]
   [blaze.http.util :as hu]
   [ring.util.request :as request]))

(defn- query-params [{:keys [query-string]} encoding]
  (if query-string
    (hu/form-decode query-string encoding)
    {}))

(defn- form-params [{:keys [body] :as request} encoding]
  (if (and body (request/urlencoded-form? request))
    (hu/form-decode (slurp body :encoding encoding) encoding)
    {}))

(defn- params-request [request]
  (let [encoding (or (request/character-encoding request) "UTF-8")]
    (if-ok [form-params (form-params request encoding)
            query-params (query-params request encoding)]
      (assoc request
             :query-params query-params
             :form-params form-params
             :params (merge form-params query-params))
      #(assoc request ::anomaly %))))

(defn wrap-params
  "Middleware to decode urlencoded parameters from the query string and the
  body, if the request is an urlencoded form. Adds the following keys to the
  request:

  :query-params - a map of parameters from the query string
  :form-params  - a map of parameters from the body
  :params       - a merged map of all parameters, query params taking
                  precedence

  Adds the anomaly under ::anomaly instead, if a parameter isn't properly
  percent-encoded. Such requests have to be rejected by `wrap-invalid-params`."
  [handler]
  (fn
    ([request]
     (handler (params-request request)))
    ([request respond raise]
     (handler (params-request request) respond raise))))

(defn wrap-invalid-params
  "Returns a handler that completes with the anomaly `wrap-params` added to the
  request, if any. Calls `handler` otherwise."
  [handler]
  (fn [{::keys [anomaly] :as request}]
    (if anomaly
      (ac/completed-future anomaly)
      (handler request))))

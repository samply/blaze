(ns blaze.middleware.json
  (:require
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [cognitect.anomalies :as anom]
    [manifold.deferred :as md]
    [ring.util.response :as ring]))


(defn- parse-json [body]
  (try
    (binding [*use-bigdecimals?* true]
      (json/parse-string (slurp body)))
    (catch Exception e
      #::anom{:category ::anom/incorrect :message (.getMessage e)})))


(defn- generate-json [body]
  (try
    (json/generate-string body {:key-fn name})
    (catch Exception e
      (json/generate-string {:error (.getMessage e)}))))


(defn wrap-json
  "Parses the request body as JSON, calls `handler` and generates JSON from the
  response."
  [handler]
  (fn [{:keys [body] :as request}]
    (let [body (parse-json body)]
      (if-let [message (::anom/message body)]
        (-> (ring/bad-request message)
            (ring/content-type "text/plain"))
        (md/let-flow' [response (handler (assoc request :body body))]
          (-> response
              (update :body generate-json)
              (assoc-in [:headers "content-type"] "application/json")))))))

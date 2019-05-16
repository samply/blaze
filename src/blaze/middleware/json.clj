(ns blaze.middleware.json
  (:require
    [blaze.handler.util :as handler-util]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.java.io :as io]
    [cognitect.anomalies :as anom]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- parse-json [body]
  (-> (md/future
        (with-open [reader (io/reader body)]
          (binding [*use-bigdecimals?* true]
            (json/parse-stream reader))))
      (md/catch'
        (fn [e]
          (md/error-deferred
            #::anom{:category ::anom/incorrect :message (.getMessage ^Exception e)})))))


(defn- generate-json [body]
  (try
    (json/generate-string body {:key-fn name})
    (catch Exception e
      (log/error (log/stacktrace e))
      (json/generate-string
        {"resourceType" "OperationOutcome"
         "issue"
         [{"severity" "error"
           "code" "exception"
           "diagnostics" (.getMessage ^Exception e)}]}))))


(defn- handle-response [{:keys [body] :as response}]
  (if (some? body)
    (-> (assoc response :body (generate-json body))
        (ring/content-type "application/fhir+json"))
    response))


(defn wrap-json
  "Parses the request body as JSON, calls `handler` and generates JSON from the
  response."
  [handler]
  (fn [{:keys [request-method body] :as request}]
    (-> (if (#{:put :post} request-method)
          (-> (parse-json body)
              (md/chain'
                (fn [parsed-body]
                  (assoc request :body parsed-body))))
          request)
        (md/chain' handler)
        (md/chain' handle-response)
        (md/catch' #(handle-response (handler-util/error-response %))))))

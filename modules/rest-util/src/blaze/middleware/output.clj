(ns blaze.middleware.output
  "Non-FHIR serialization middleware.

  Currently supported formats:
  * standard: JSON."
  (:require
   [jsonista.core :as j]
   [ring.util.response :as ring]))

(defn handle-response [object-mapper response]
  (-> (update response :body #(j/write-value-as-bytes % object-mapper))
      (ring/content-type "application/json;charset=utf-8")))

(defn wrap-output
  "Middleware to output data (not resources) in JSON."
  [opts]
  (let [object-mapper (j/object-mapper opts)]
    (fn [handler]
      (fn [request respond raise]
        (handler request #(respond (handle-response object-mapper %)) raise)))))

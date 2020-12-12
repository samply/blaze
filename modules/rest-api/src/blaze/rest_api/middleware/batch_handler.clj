(ns blaze.rest-api.middleware.batch-handler)


(defn wrap-batch-handler [handler batch-handler-promise]
  (fn [request]
    (handler (assoc request :batch-handler @batch-handler-promise))))

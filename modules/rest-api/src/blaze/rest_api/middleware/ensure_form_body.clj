(ns blaze.rest-api.middleware.ensure-form-body
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [ring.util.request :as request]))

(defn wrap-ensure-form-body [handler]
  (fn [request]
    (if (request/urlencoded-form? request)
      (handler request)
      (ac/completed-future
       (ba/incorrect
        (if (request/content-type request)
          "Unsupported Content-Type header `application/fhir+json`. Please use `application/x-www-form-urlencoded`."
          "Missing Content-Type header. Please use `application/x-www-form-urlencoded`.")
        :http/status 415)))))

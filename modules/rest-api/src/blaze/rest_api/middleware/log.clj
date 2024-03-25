(ns blaze.rest-api.middleware.log
  (:require
   [clojure.string :as str]
   [taoensso.timbre :as log]))

(defn- format-request-method [{:keys [request-method]}]
  (str/upper-case (name request-method)))

(defn format-request [{:keys [uri query-string] :as request}]
  (if query-string
    (format "%s [base]%s?%s" (format-request-method request) uri query-string)
    (format "%s [base]%s" (format-request-method request) uri)))

(defn wrap-log
  [handler]
  (fn [request respond raise]
    (log/debug (format-request request))
    (handler request respond raise)))

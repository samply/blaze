(ns blaze.rest-api.middleware.log
  (:require
    [clojure.string :as str]
    [taoensso.timbre :as log]))


(defn- format-request-method [{:keys [request-method]}]
  (str/upper-case (name request-method)))


(defn wrap-log
  [handler]
  (fn [{:keys [uri query-string] :as request} respond raise]
    (log/debug
      (if query-string
        (format "%s [base]%s?%s" (format-request-method request) uri query-string)
        (format "%s [base]%s" (format-request-method request) uri)))
    (handler request respond raise)))

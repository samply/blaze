(ns blaze.rest-api.middleware.log
  (:require
    [clojure.string :as str]
    [taoensso.timbre :as log]))


(defn wrap-log
  [handler]
  (fn [{:keys [request-method uri query-string] :as request}]
    (log/debug (format "%s [base]%s%s" (str/upper-case (name request-method)) uri (or query-string "")))
    (handler request)))

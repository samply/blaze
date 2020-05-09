(ns blaze.rest-api.middleware.log
  (:require
    [clojure.string :as str]
    [taoensso.timbre :as log]))


(defn wrap-log
  [handler]
  (fn [{:keys [request-method uri query-string] :as request}]
    (log/debug
      (if query-string
        (format "%s [base]%s?%s" (str/upper-case (name request-method)) uri query-string)
        (format "%s [base]%s" (str/upper-case (name request-method)) uri)))
    (handler request)))

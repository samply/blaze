(ns blaze.job.async-interaction.request
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.job-scheduler :as js]
   [blaze.job.async-interaction :as job-async]
   [blaze.luid :as luid]
   [clojure.string :as str]
   [java-time.api :as time]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- strip-context-path [context-path uri]
  (subs uri (inc (count context-path))))

(defn- luid [{:keys [clock rng-fn]}]
  (luid/luid clock (rng-fn)))

(defn- request-bundle [id context-path {:keys [request-method uri query-string]}]
  (job-async/request-bundle id (str/upper-case (name request-method))
                            (cond-> (strip-context-path context-path uri)
                              (seq query-string)
                              (str "?" query-string))))

(defn- async-status-url
  [{:keys [context-path]} {:blaze/keys [base-url]} {:keys [id]}]
  (str base-url context-path "/__async-status/" id))

(defn handle-async
  {:arglists '([context request])}
  [{:keys [context-path clock] :or {context-path ""} :as context}
   {:blaze/keys [job-scheduler db] :as request}]
  (let [authored-on (time/offset-date-time clock)
        bundle-id (luid context)]
    (log/debug "Initiate async response...")
    (do-sync [job (js/create-job job-scheduler
                                 (job-async/job authored-on bundle-id (d/t db))
                                 (request-bundle bundle-id context-path request))]
      (-> (ring/status 202)
          (ring/header "Content-Location" (async-status-url context request job))))))

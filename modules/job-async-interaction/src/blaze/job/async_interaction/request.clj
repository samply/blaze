(ns blaze.job.async-interaction.request
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.handler.util :as handler-util]
   [blaze.job-scheduler :as js]
   [blaze.job.async-interaction :as job-async]
   [blaze.module :as m]
   [blaze.util :refer [str]]
   [clojure.string :as str]
   [java-time.api :as time]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- strip-context-path [context-path uri]
  (subs uri (inc (count context-path))))

(defn- request-bundle
  [id context-path {:keys [request-method uri headers query-string body]}]
  (let [return-preference (handler-util/preference headers "return")]
    (job-async/request-bundle
     id (str/upper-case (name request-method))
     (cond-> (strip-context-path context-path uri)
       (seq query-string)
       (str "?" query-string))
     (when (and (= :post request-method) (map? body)) body)
     (cond-> {}
       return-preference (assoc :blaze.preference/return return-preference)))))

(defn handle-async
  {:arglists '([context request])}
  [{:keys [context-path clock] :or {context-path ""} :as context}
   {:blaze/keys [job-scheduler db] :as request}]
  (let [authored-on (time/offset-date-time clock)
        bundle-id (m/luid context)]
    (log/debug "Initiate async response...")
    (do-sync [job (js/create-job job-scheduler
                                 (job-async/job authored-on bundle-id (d/t db))
                                 (request-bundle bundle-id context-path request))]
      (-> (ring/status 202)
          (handler-util/async-status-location context request job)))))

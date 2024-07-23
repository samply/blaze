(ns blaze.rest-api.async-status-cancel-handler
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.job-scheduler :as js]
   [blaze.job-scheduler.spec]
   [blaze.module :as m]
   [blaze.rest-api :as-alias rest-api]
   [blaze.rest-api.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- conflict-msg [id status]
  (format "The asynchronous request with id `%s` can't be cancelled because it's already %s." id status))

(defn- handler [job-scheduler]
  (fn [{{:keys [id]} :path-params}]
    (-> (js/cancel-job job-scheduler id)
        (ac/then-apply
         (fn [_]
           (ring/status 202)))
        (ac/exceptionally
         (fn [{:job/keys [status] :as anom}]
           (cond-> anom
             (and (ba/conflict? anom) status)
             (assoc ::anom/message (conflict-msg id status))))))))

(defmethod m/pre-init-spec ::rest-api/async-status-cancel-handler [_]
  (s/keys :req-un [:blaze/job-scheduler]))

(defmethod ig/init-key ::rest-api/async-status-cancel-handler
  [_ {:keys [job-scheduler]}]
  (log/info "Init async status cancel handler")
  (handler job-scheduler))

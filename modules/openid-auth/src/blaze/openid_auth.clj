(ns blaze.openid-auth
  (:require
   [blaze.anomaly :refer [if-ok]]
   [blaze.http-client.spec]
   [blaze.module :as m]
   [blaze.openid-auth.impl :as impl]
   [blaze.openid-auth.spec]
   [blaze.scheduler :as sched]
   [blaze.scheduler.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(defn- schedule [{:keys [scheduler http-client provider-url]} public-keys-state]
  (sched/schedule-at-fixed-rate
   scheduler
   #(do
      (log/debug "Fetch public key(s) from" provider-url "...")
      (if-ok [public-keys (impl/fetch-public-keys http-client provider-url)]
        (do (reset! public-keys-state public-keys)
            (log/debug "Done fetching" (count public-keys) "public key(s) from" provider-url))
        (fn [{::anom/keys [message]}]
          (log/error (format "Error while fetching public key(s) from %s: %s" provider-url message)))))
   (time/seconds 1) (time/seconds 60)))

(defmethod m/pre-init-spec :blaze.openid-auth/backend [_]
  (s/keys :req-un [:blaze/http-client :blaze/scheduler ::provider-url]))

(defmethod ig/init-key :blaze.openid-auth/backend
  [_ {:keys [provider-url] :as context}]
  (log/info "Start OpenID authentication backend with provider:" provider-url)
  (let [public-keys-state (atom nil)]
    (impl/->Backend (schedule context public-keys-state) public-keys-state)))

(defmethod ig/halt-key! :blaze.openid-auth/backend
  [_ {:keys [future]}]
  (log/info "Stop OpenID authentication backend")
  (sched/cancel future false))

(derive :blaze.openid-auth/backend :blaze.auth/backend)

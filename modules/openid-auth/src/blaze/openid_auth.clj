(ns blaze.openid-auth
  (:require
    [blaze.openid-auth.impl :as impl]
    [blaze.openid-auth.spec]
    [blaze.scheduler :as sched]
    [blaze.scheduler.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [java-time :as jt]
    [taoensso.timbre :as log])
  (:import
    [java.security PublicKey]
    [java.util.concurrent Future]))


(set! *warn-on-reflection* true)


(defn- schedule [{:keys [scheduler http-client provider-url]} secret-state]
  (sched/schedule-at-fixed-rate
    scheduler
    #(try
       (log/info "Fetch public key from" provider-url "...")
       (let [^PublicKey public-key (impl/fetch-public-key http-client provider-url)]
         (reset! secret-state public-key)
         (log/info "Done fetching public key from" provider-url
                   (str "algorithm=" (.getAlgorithm public-key))
                   (str "format=" (.getFormat public-key))))
       (catch Exception e
         (log/error (format "Error while fetching public key from %s:"
                            provider-url)
                    (ex-message e) (pr-str (ex-data e)))))
    (jt/seconds 1) (jt/seconds 60)))



(defmethod ig/pre-init-spec :blaze.openid-auth/backend [_]
  (s/keys :req-un [:blaze/http-client :blaze/scheduler ::provider-url]))


(defmethod ig/init-key :blaze.openid-auth/backend
  [_ {:keys [provider-url] :as context}]
  (log/info "Start OpenID authentication backend with provider:" provider-url)
  (let [secret-state (atom nil)]
    (impl/->Backend (schedule context secret-state) secret-state)))


(defmethod ig/halt-key! :blaze.openid-auth/backend
  [_ {:keys [future]}]
  (log/info "Stop OpenID authentication backend")
  (.cancel ^Future future false))


(derive :blaze.openid-auth/backend :blaze.auth/backend)

(ns blaze.openid-auth
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.http-client.spec]
   [blaze.module :as m]
   [blaze.openid-auth.impl :as impl]
   [blaze.openid-auth.spec]
   [blaze.scheduler :as sched]
   [blaze.scheduler.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def ^:private ^:const default-realm
  "The realm used in legacy mode without issuer."
  "Blaze")

(defn- log-skipped
  "Logs the messages of the skipped keys at WARN if they differ from the ones
  of the previous fetch, so that an unchanged JWKS doesn't warn every minute,
  and at DEBUG otherwise."
  [previous-skipped skipped]
  (if (= previous-skipped skipped)
    (run! #(log/debug %) skipped)
    (run! #(log/warn %) skipped)))

(defn- fetch! [{:keys [http-client provider-url]} key-state]
  (log/debug "Fetch public key(s) from" provider-url "...")
  (let [result (impl/fetch-public-keys http-client provider-url)
        [old new] (swap-vals! key-state impl/key-state result)]
    (log-skipped (:skipped old) (:skipped new))
    (if-ok [{:keys [keys]} result]
      (log/debug "Done fetching" (count keys) "public key(s) from" provider-url)
      (fn [{::anom/keys [message]}]
        (log/error (format "Error while fetching public key(s) from %s: %s" provider-url message))))))

(defn- schedule [{:keys [scheduler] :as config} key-state]
  (sched/schedule-at-fixed-rate
   scheduler #(fetch! config key-state) (time/seconds 1) (time/seconds 60)))

(defmethod m/pre-init-spec :blaze.openid-auth/backend [_]
  (s/keys :req-un [:blaze/http-client :blaze/scheduler :blaze/clock ::provider-url]
          :opt-un [::issuer ::audience ::legacy-mode?]))

(defn- missing-vars [{:keys [issuer audience]}]
  (cond-> []
    (nil? issuer) (conj "OPENID_PROVIDER_ISSUER")
    (nil? audience) (conj "OPENID_AUDIENCE")))

(defn- missing-vars-msg [vars]
  (format "%s %s not set. In RFC mode, both OPENID_PROVIDER_ISSUER and OPENID_AUDIENCE are required. Set them, or set OPENID_LEGACY_MODE=true to keep the deprecated legacy validation."
          (str/join " and " vars) (if (= 1 (count vars)) "is" "are")))

(defn- check-rfc-mode-config [config]
  (when-let [vars (seq (missing-vars config))]
    (ba/throw-anom (ba/incorrect (missing-vars-msg vars)))))

(defn- warn-legacy-mode! [{:keys [issuer audience]}]
  (log/warn "OPENID_LEGACY_MODE is enabled. The legacy mode is deprecated and planned for removal in the next major version.")
  (when-not issuer
    (log/warn "OPENID_PROVIDER_ISSUER is not set; issuer (iss) claim will not be validated."))
  (when-not audience
    (log/warn "OPENID_AUDIENCE is not set; audience (aud) claim will not be validated.")))

(defn- verify-opts [{:keys [issuer audience legacy-mode?]}]
  (cond-> {:legacy-mode? (boolean legacy-mode?)}
    issuer (assoc :issuer issuer)
    audience (assoc :audience audience)))

(defmethod ig/init-key :blaze.openid-auth/backend
  [_ {:keys [provider-url clock issuer legacy-mode?] :as config}]
  (log/info "Start OpenID authentication backend with provider:" provider-url)
  (if legacy-mode?
    (warn-legacy-mode! config)
    (check-rfc-mode-config config))
  (let [key-state (atom nil)]
    (impl/->Backend (schedule config key-state) key-state clock
                    (verify-opts config) (or issuer default-realm))))

(defmethod ig/halt-key! :blaze.openid-auth/backend
  [_ {:keys [future]}]
  (log/info "Stop OpenID authentication backend")
  (sched/cancel future false))

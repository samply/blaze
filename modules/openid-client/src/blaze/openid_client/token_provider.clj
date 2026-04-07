(ns blaze.openid-client.token-provider
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.http-client.spec]
   [blaze.module :as m]
   [blaze.openid-client.token-provider.impl :as impl]
   [blaze.openid-client.token-provider.protocol :as p]
   [blaze.openid-client.token-provider.spec]
   [blaze.scheduler :as sched]
   [blaze.scheduler.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [hato.client :as hc]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def ^:private ^:const well-known-uri "/.well-known/openid-configuration")

(defn- fetch-config [http-client uri]
  (try
    (:body (hc/get uri {:http-client http-client :accept :json :as :json}))
    (catch Throwable _
      (ba/fault (str "Error while fetching the OpenID config from: " uri)))))

(defn- missing-token-endpoint-msg [provider-url]
  (format "Missing `token_endpoint` in OpenID config at `%s`." provider-url))

(defn- post-token [http-client token-endpoint client-id client-secret]
  (try
    (let [response (:body (hc/post token-endpoint
                                   {:http-client http-client
                                    :form-params {:grant_type "client_credentials"
                                                  :client_id client-id
                                                  :client_secret client-secret}
                                    :accept :json
                                    :as :json}))]
      {:token (:access_token response)
       :expires-at (when-let [expires-in (:expires_in response)]
                     (time/plus (time/instant) (time/seconds (long expires-in))))})
    (catch Throwable _
      (ba/fault (str "Error while obtaining a token from: " token-endpoint)))))

(defn- fetch-token [http-client provider-url client-id client-secret]
  (when-ok [config (fetch-config http-client (str provider-url well-known-uri))]
    (if-let [token-endpoint (:token_endpoint config)]
      (post-token http-client token-endpoint client-id client-secret)
      (ba/fault (missing-token-endpoint-msg provider-url)))))

(defn- refresh! [http-client provider-url client-id client-secret token-state]
  (when (impl/should-refresh? @token-state)
    (log/debug "Fetching token from" provider-url "...")
    (if-ok [new-state (fetch-token http-client provider-url client-id client-secret)]
      (do (reset! token-state new-state)
          (log/debug "Done fetching token from" provider-url "expires at" (str (:expires-at new-state))))
      (fn [{::anom/keys [message]}]
        (reset! token-state {::anom/category ::anom/fault ::anom/message message})
        (log/error (format "Error while fetching token from %s: %s" provider-url message))))))

(defn current-token
  "Returns the current access token string or an anomaly if no token is
  available."
  [token-provider]
  (p/-current-token token-provider))

(defrecord TokenProvider [token-state future]
  p/TokenProvider
  (-current-token [_]
    (impl/token @token-state)))

(defmethod m/pre-init-spec :blaze.openid-client/token-provider [_]
  (s/keys :req-un [:blaze/http-client :blaze/scheduler ::provider-url
                   ::client-id ::client-secret]))

(defmethod ig/init-key :blaze.openid-client/token-provider
  [_ {:keys [http-client scheduler provider-url client-id client-secret]}]
  (log/info "Init OpenID client token provider with provider:" provider-url)
  (let [token-state (atom nil)]
    (->TokenProvider
     token-state
     (sched/schedule-at-fixed-rate
      scheduler
      #(refresh! http-client provider-url client-id client-secret
                 token-state)
      (time/seconds 0) (time/minutes 1)))))

(defmethod ig/halt-key! :blaze.openid-client/token-provider
  [_ {:keys [future]}]
  (log/info "Stop OpenID client token provider")
  (sched/cancel future false))

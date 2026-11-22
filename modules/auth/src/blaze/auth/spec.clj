(ns blaze.auth.spec
  (:require
   [blaze.auth.protocols :as p]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/def :blaze.auth/backend
  #(satisfies? p/AuthenticationBackend %))

(s/def :blaze.auth/realm
  (s/and string? #(re-matches #"[^\"\\\p{Cntrl}]+" %)))

(s/def :blaze.auth/error
  #{"invalid_request" "invalid_token" "insufficient_scope"})

(s/def :blaze.auth/identity
  some?)

(s/def :blaze.auth/failure
  (s/and ::anom/anomaly (s/keys :req [:blaze.auth/realm] :opt [:blaze.auth/error])))

(ns blaze.middleware.authentication-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.auth.protocols :as p]
   [blaze.middleware.authentication :refer [wrap-authentication]]
   [blaze.middleware.authentication-spec]
   [blaze.module.test-util.ring :refer [call]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private backend
  (reify p/AuthenticationBackend
    (-authenticate [_ {{:strs [authorization]} :headers}]
      (cond
        (= "Bearer token-150418" authorization)
        {:sub "user-150425"}

        (some? authorization)
        (ba/forbidden "Invalid token."
                      :blaze.auth/realm "realm-150436"
                      :blaze.auth/error "invalid_token")

        :else
        (ba/incorrect "No token." :blaze.auth/realm "realm-150436")))))

(defn- handler [request respond _]
  (respond request))

(deftest wrap-authentication-test
  (testing "authenticated"
    (given (call (wrap-authentication handler backend)
                 {:headers {"authorization" "Bearer token-150418"}})
      [:identity :sub] := "user-150425"
      :blaze.auth/failure := nil))

  (testing "without credentials"
    (given (call (wrap-authentication handler backend) {})
      :identity := nil
      [:blaze.auth/failure ::anom/category] := ::anom/incorrect
      [:blaze.auth/failure :blaze.auth/realm] := "realm-150436"))

  (testing "with rejected credentials"
    (given (call (wrap-authentication handler backend)
                 {:headers {"authorization" "Bearer invalid"}})
      :identity := nil
      [:blaze.auth/failure ::anom/category] := ::anom/forbidden
      [:blaze.auth/failure :blaze.auth/realm] := "realm-150436"
      [:blaze.auth/failure :blaze.auth/error] := "invalid_token")))

(ns blaze.middleware.fhir.auth-guard-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.middleware.fhir.auth-guard :refer [wrap-auth-guard]]
   [blaze.middleware.fhir.auth-guard-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]
   [ring.util.response :as ring]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn handler [_]
  (ac/completed-future (ring/response ::foo)))

(deftest wrap-auth-guard-test
  (testing "with identity"
    (given @((wrap-auth-guard handler) {:identity :bar})
      :status := 200
      :body := ::foo))

  (testing "without identity"
    (given @((wrap-auth-guard handler) {:request-method :get})
      :status := 401
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code "error"
      [:body :issue 0 :code] := #fhir/code "login"
      [:body :issue 0 :details :coding 0 :system] := #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome"
      [:body :issue 0 :details :coding 0 :code] := #fhir/code "MSG_AUTH_REQUIRED"
      [:headers "WWW-Authenticate"] := nil))

  (testing "without credentials"
    (given @((wrap-auth-guard handler)
             {:request-method :get
              :blaze.auth/failure
              (ba/incorrect "No token." :blaze.auth/realm "realm-152236")})
      :status := 401
      [:headers "WWW-Authenticate"] := "Bearer realm=\"realm-152236\""))

  (testing "with rejected credentials"
    (given @((wrap-auth-guard handler)
             {:request-method :get
              :blaze.auth/failure
              (ba/forbidden "Invalid token."
                            :blaze.auth/realm "realm-152236"
                            :blaze.auth/error "invalid_token")})
      :status := 401
      [:headers "WWW-Authenticate"] := "Bearer realm=\"realm-152236\", error=\"invalid_token\""))

  (testing "authentication unavailable"
    (given @((wrap-auth-guard handler)
             {:request-method :get
              :blaze.auth/failure
              (ba/unavailable "msg-112512" :blaze.auth/realm "realm-152236")})
      :status := 503
      [:headers "WWW-Authenticate"] := nil
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code "error"
      [:body :issue 0 :code] := #fhir/code "transient"
      [:body :issue 0 :diagnostics] := #fhir/string "msg-112512")))

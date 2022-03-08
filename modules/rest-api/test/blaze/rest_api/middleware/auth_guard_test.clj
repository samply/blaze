(ns blaze.rest-api.middleware.auth-guard-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.rest-api.middleware.auth-guard :refer [wrap-auth-guard]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [juxt.iota :refer [given]]
    [ring.util.response :as ring]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn handler [_]
  (ac/completed-future (ring/response ::foo)))


(deftest wrap-auth-guard-test
  (testing "with identity"
    (given @((wrap-auth-guard handler) {:identity :bar})
      :status := 200
      :body := ::foo))

  (testing "without identity"
    (given @((wrap-auth-guard handler) {})
      :status := 401
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"login"
      [:body :issue 0 :details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
      [:body :issue 0 :details :coding 0 :code] := #fhir/code"MSG_AUTH_REQUIRED")))

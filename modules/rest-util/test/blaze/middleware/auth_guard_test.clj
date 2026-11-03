(ns blaze.middleware.auth-guard-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.middleware.auth-guard :refer [wrap-auth-guard]]
   [blaze.middleware.auth-guard-spec]
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
    (given @((wrap-auth-guard handler) {:request-method :get :uri "/foo"})
      :status := 401
      :body := {:msg "Authentication required."})))

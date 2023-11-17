(ns blaze.openid-auth-test
  (:require
   [blaze.module.test-util :refer [with-system]]
   [blaze.openid-auth :as openid-auth]
   [blaze.openid-auth.spec]
   [blaze.test-util :as tu :refer [given-thrown]]
   [buddy.auth.protocols :as p]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient HttpClientMock]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(defmethod ig/init-key ::http-client [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnStatus 404))))

(def config
  {::openid-auth/backend
   {:http-client (ig/ref ::http-client)
    :scheduler (ig/ref :blaze/scheduler)
    :provider-url "http://localhost:8080"}
   ::http-client {}
   :blaze/scheduler {}})

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::openid-auth/backend nil})
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::openid-auth/backend {}})
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :provider-url)))))

(deftest backend-test
  (with-system [{::openid-auth/keys [backend]} config]
    (is (satisfies? p/IAuthentication backend))
    (Thread/sleep 2000)))

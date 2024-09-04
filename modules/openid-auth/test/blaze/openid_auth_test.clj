(ns blaze.openid-auth-test
  (:require
   [blaze.http-client.spec :refer [http-client?]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.openid-auth :as openid-auth]
   [blaze.openid-auth.impl-test :refer [jwks-document-one-key]]
   [blaze.openid-auth.spec]
   [blaze.scheduler.spec :refer [scheduler?]]
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
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::openid-auth/backend nil})
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {::openid-auth/backend {}})
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))))

  (testing "invalid http-client"
    (given-thrown (ig/init {::openid-auth/backend {:http-client ::invalid}})
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))
      [:cause-data ::s/problems 2 :pred] := `http-client?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid scheduler"
    (given-thrown (ig/init {::openid-auth/backend {:scheduler ::invalid}})
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))
      [:cause-data ::s/problems 2 :pred] := `scheduler?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid provider-url"
    (given-thrown (ig/init {::openid-auth/backend {:provider-url ::invalid}})
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `string?
      [:cause-data ::s/problems 2 :val] := ::invalid)))

(defmethod ig/init-key ::http-client-not-found [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnStatus 404))
    http-client))

(defmethod ig/init-key ::http-client-success [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))
    (-> (.onGet http-client "http://localhost:8080/jwks")
        (.doReturnJSON jwks-document-one-key))
    http-client))

(def config-not-found
  {::openid-auth/backend
   {:http-client (ig/ref ::http-client-not-found)
    :scheduler (ig/ref :blaze/scheduler)
    :provider-url "http://localhost:8080"}
   ::http-client-not-found {}
   :blaze/scheduler {}})

(def config-success
  {::openid-auth/backend
   {:http-client (ig/ref ::http-client-success)
    :scheduler (ig/ref :blaze/scheduler)
    :provider-url "http://localhost:8080"}
   ::http-client-success {}
   :blaze/scheduler {}})

(deftest backend-test
  (testing "public key not found"
    (with-system [{::openid-auth/keys [backend]} config-not-found]
      (is (satisfies? p/IAuthentication backend))
      (Thread/sleep 2000)))

  (testing "public key found"
    (with-system [{::openid-auth/keys [backend]} config-success]
      (is (satisfies? p/IAuthentication backend))
      (Thread/sleep 2000))))

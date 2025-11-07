(ns blaze.openid-auth-test
  (:require
   [blaze.http-client]
   [blaze.http-client.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.openid-auth :as openid-auth]
   [blaze.openid-auth.spec]
   [blaze.scheduler.spec]
   [blaze.test-util :as tu]
   [buddy.auth.protocols :as p]
   [clojure.java.io :as io]
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
        (.doReturnJSON (slurp (io/resource "blaze/openid_auth/google.json"))))
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

(deftest init-test
  (testing "nil config"
    (given-failed-system {::openid-auth/backend nil}
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::openid-auth/backend {}}
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))))

  (testing "invalid http-client"
    (given-failed-system (assoc-in config-success [::openid-auth/backend :http-client] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/http-client]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid scheduler"
    (given-failed-system (assoc-in config-success [::openid-auth/backend :scheduler] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/scheduler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid provider-url"
    (given-failed-system (assoc-in config-success [::openid-auth/backend :provider-url] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::openid-auth/provider-url]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest backend-test
  (testing "public key not found"
    (with-system [{::openid-auth/keys [backend]} config-not-found]
      (is (satisfies? p/IAuthentication backend))
      (Thread/sleep 2000)
      (is (nil? (p/-authenticate backend {} "")))))

  (testing "public key found"
    (with-system [{::openid-auth/keys [backend]} config-success]
      (is (satisfies? p/IAuthentication backend))
      (Thread/sleep 2000)
      (is (nil? (p/-authenticate backend {} ""))))))

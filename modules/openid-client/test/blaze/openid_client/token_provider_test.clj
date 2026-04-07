(ns blaze.openid-client.token-provider-test
  (:require
   [blaze.http-client]
   [blaze.http-client.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.openid-client :as-alias oic]
   [blaze.openid-client.token-provider :as tp]
   [blaze.openid-client.token-provider-spec]
   [blaze.scheduler.spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient HttpClientMock]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defmethod ig/init-key ::http-client-config-not-found [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnStatus 404))
    http-client))

(defmethod ig/init-key ::http-client-config-no-token-endpoint [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{}"))
    http-client))

(defmethod ig/init-key ::http-client-token-error [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{\"token_endpoint\":\"http://localhost:8080/token\"}"))
    (-> (.onPost http-client "http://localhost:8080/token")
        (.doReturnStatus 403))
    http-client))

(defmethod ig/init-key ::http-client-success [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{\"token_endpoint\":\"http://localhost:8080/token\"}"))
    (-> (.onPost http-client "http://localhost:8080/token")
        (.doReturnJSON "{\"access_token\":\"my-token\",\"expires_in\":3600}"))
    http-client))

(def ^:private base-config
  {::oic/token-provider
   {:scheduler (ig/ref :blaze/scheduler)
    :provider-url "http://localhost:8080"
    :client-id "my-client"
    :client-secret "my-secret"}
   :blaze/scheduler {}})

(def ^:private config-config-not-found
  (-> (assoc-in base-config [::oic/token-provider :http-client]
                (ig/ref ::http-client-config-not-found))
      (assoc ::http-client-config-not-found {})))

(def ^:private config-config-no-token-endpoint
  (-> (assoc-in base-config [::oic/token-provider :http-client]
                (ig/ref ::http-client-config-no-token-endpoint))
      (assoc ::http-client-config-no-token-endpoint {})))

(def ^:private config-token-error
  (-> (assoc-in base-config [::oic/token-provider :http-client]
                (ig/ref ::http-client-token-error))
      (assoc ::http-client-token-error {})))

(def ^:private config-success
  (-> (assoc-in base-config [::oic/token-provider :http-client]
                (ig/ref ::http-client-success))
      (assoc ::http-client-success {})))

(deftest init-test
  (testing "nil config"
    (given-failed-system {::oic/token-provider nil}
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {::oic/token-provider {}}
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :http-client))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :client-id))
      [:cause-data ::s/problems 4 :pred] := `(fn ~'[%] (contains? ~'% :client-secret))))

  (testing "invalid http-client"
    (given-failed-system (assoc-in config-success [::oic/token-provider :http-client] ::invalid)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/http-client]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid scheduler"
    (given-failed-system (assoc-in config-success [::oic/token-provider :scheduler] ::invalid)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/scheduler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid provider-url"
    (given-failed-system (assoc-in config-success [::oic/token-provider :provider-url] ::invalid)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::tp/provider-url]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid client-id"
    (given-failed-system (assoc-in config-success [::oic/token-provider :client-id] ::invalid)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::tp/client-id]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid client-secret"
    (given-failed-system (assoc-in config-success [::oic/token-provider :client-secret] ::invalid)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::tp/client-secret]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest current-token-test
  (testing "config not found"
    (with-system [{::oic/keys [token-provider]} config-config-not-found]
      (Thread/sleep 100)

      (given (tp/current-token token-provider)
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID config from: http://localhost:8080/.well-known/openid-configuration")))

  (testing "config no token endpoint"
    (with-system [{::oic/keys [token-provider]} config-config-no-token-endpoint]
      (Thread/sleep 100)

      (given (tp/current-token token-provider)
        ::anom/category := ::anom/fault
        ::anom/message := "Missing `token_endpoint` in OpenID config at `http://localhost:8080`.")))

  (testing "token error"
    (with-system [{::oic/keys [token-provider]} config-token-error]
      (Thread/sleep 100)

      (given (tp/current-token token-provider)
        ::anom/category := ::anom/fault
        ::anom/message := "Error while obtaining a token from: http://localhost:8080/token")))

  (testing "token fetch succeeds"
    (with-system [{::oic/keys [token-provider]} config-success]
      (given (tp/current-token token-provider)
        ::anom/category := ::anom/unavailable)

      (Thread/sleep 100)

      (is (= "my-token" (tp/current-token token-provider))))))

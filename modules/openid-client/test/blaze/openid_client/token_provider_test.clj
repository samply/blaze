(ns blaze.openid-client.token-provider-test
  (:require
   [blaze.http-client]
   [blaze.http-client.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.openid-client :as-alias oic]
   [blaze.openid-client.token-provider :as tp]
   [blaze.openid-client.token-provider-spec]
   [blaze.scheduler.spec]
   [blaze.scheduler.test-util :as stu]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
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

(defmethod ig/init-key ::http-client [_ {:keys [config token]}]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON config))
    (-> (.onPost http-client "http://localhost:8080/token")
        (.doReturnJSON token))
    http-client))

(def ^:private config-doc "{\"token_endpoint\":\"http://localhost:8080/token\"}")
(def ^:private token-doc "{\"access_token\":\"my-token\",\"expires_in\":3600}")

(defn- padded
  "Returns the ASCII JSON document `json` padded with trailing whitespace to
  exactly `n` bytes."
  [json n]
  (str json (str/join (repeat (- n (count json)) " "))))

(def ^:private base-config
  {::oic/token-provider
   {:scheduler (ig/ref :blaze.test/manual-scheduler)
    :provider-url "http://localhost:8080"
    :client-id "my-client"
    :client-secret "my-secret"}
   :blaze.test/manual-scheduler {}})

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

  (testing "missing scheduler"
    (given-failed-system (update config-success ::oic/token-provider dissoc :scheduler)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))))

  (testing "missing provider-url"
    (given-failed-system (update config-success ::oic/token-provider dissoc :provider-url)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))))

  (testing "missing client-id"
    (given-failed-system (update config-success ::oic/token-provider dissoc :client-id)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :client-id))))

  (testing "missing client-secret"
    (given-failed-system (update config-success ::oic/token-provider dissoc :client-secret)
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :client-secret))))

  (testing "missing scheduler through client-secret"
    (given-failed-system {::oic/token-provider {:http-client ::invalid}}
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :client-id))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :client-secret))))

  (testing "missing provider-url through client-secret"
    (given-failed-system {::oic/token-provider {:http-client ::invalid :scheduler ::invalid}}
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :client-id))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :client-secret))))

  (testing "missing client-id and client-secret"
    (given-failed-system {::oic/token-provider {:http-client ::invalid :scheduler ::invalid :provider-url ::invalid}}
      :key := ::oic/token-provider
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :client-id))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :client-secret))))

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

(defn- config-docs [config token]
  (-> (assoc-in base-config [::oic/token-provider :http-client] (ig/ref ::http-client))
      (assoc ::http-client {:config config :token token})))

(defn- token-after-tick [config]
  (with-system [{::oic/keys [token-provider]
                 :blaze.test/keys [manual-scheduler]} config]
    (stu/tick! manual-scheduler)
    (tp/current-token token-provider)))

(deftest strict-json-test
  (testing "OpenID configuration of 1 MiB"
    (is (= "my-token" (token-after-tick (config-docs (padded config-doc 1048576) token-doc)))))

  (testing "OpenID configuration of 1 MiB and one byte more"
    (given (token-after-tick (config-docs (padded config-doc 1048577) token-doc))
      ::anom/category := ::anom/fault
      ::anom/message := "Error while fetching the OpenID config from: http://localhost:8080/.well-known/openid-configuration"))

  (testing "OpenID configuration with duplicate keys"
    (given (token-after-tick (config-docs "{\"token_endpoint\":\"http://localhost:8080/token\",\"token_endpoint\":\"http://localhost:8080/token\"}" token-doc))
      ::anom/category := ::anom/fault
      ::anom/message := "Error while fetching the OpenID config from: http://localhost:8080/.well-known/openid-configuration"))

  (testing "token response of 1 MiB"
    (is (= "my-token" (token-after-tick (config-docs config-doc (padded token-doc 1048576))))))

  (testing "token response of 1 MiB and one byte more"
    (given (token-after-tick (config-docs config-doc (padded token-doc 1048577)))
      ::anom/category := ::anom/fault
      ::anom/message := "Error while obtaining a token from: http://localhost:8080/token"))

  (testing "token response with duplicate keys"
    (given (token-after-tick (config-docs config-doc "{\"access_token\":\"a\",\"access_token\":\"b\"}"))
      ::anom/category := ::anom/fault
      ::anom/message := "Error while obtaining a token from: http://localhost:8080/token"))

  (testing "access token of 64 KiB"
    (let [token (str/join (repeat 65536 "a"))]
      (is (= token (token-after-tick (config-docs config-doc (format "{\"access_token\":\"%s\"}" token)))))))

  (testing "access token of 64 KiB and one character more"
    (given (token-after-tick (config-docs config-doc (format "{\"access_token\":\"%s\"}" (str/join (repeat 65537 "a")))))
      ::anom/category := ::anom/fault
      ::anom/message := "Error while obtaining a token from: http://localhost:8080/token"))

  (testing "nesting of 8 levels"
    (is (= "my-token" (token-after-tick (config-docs config-doc "{\"access_token\":\"my-token\",\"x\":[[[[[[[1]]]]]]]}")))))

  (testing "nesting of 9 levels"
    (given (token-after-tick (config-docs config-doc "{\"access_token\":\"my-token\",\"x\":[[[[[[[[1]]]]]]]]}"))
      ::anom/category := ::anom/fault)))

(deftest current-token-test
  (testing "config not found"
    (with-system [{::oic/keys [token-provider]
                   :blaze.test/keys [manual-scheduler]} config-config-not-found]
      (stu/tick! manual-scheduler)

      (given (tp/current-token token-provider)
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID config from: http://localhost:8080/.well-known/openid-configuration")))

  (testing "config no token endpoint"
    (with-system [{::oic/keys [token-provider]
                   :blaze.test/keys [manual-scheduler]} config-config-no-token-endpoint]
      (stu/tick! manual-scheduler)

      (given (tp/current-token token-provider)
        ::anom/category := ::anom/fault
        ::anom/message := "Missing `token_endpoint` in OpenID config at `http://localhost:8080`.")))

  (testing "token error"
    (with-system [{::oic/keys [token-provider]
                   :blaze.test/keys [manual-scheduler]} config-token-error]
      (stu/tick! manual-scheduler)

      (given (tp/current-token token-provider)
        ::anom/category := ::anom/fault
        ::anom/message := "Error while obtaining a token from: http://localhost:8080/token")))

  (testing "token fetch succeeds"
    (with-system [{::oic/keys [token-provider]
                   :blaze.test/keys [manual-scheduler]} config-success]
      (testing "no token is available before the first fetch"
        (given (tp/current-token token-provider)
          ::anom/category := ::anom/unavailable))

      (stu/tick! manual-scheduler)

      (is (= "my-token" (tp/current-token token-provider))))))

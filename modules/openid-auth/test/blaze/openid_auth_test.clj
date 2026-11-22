(ns blaze.openid-auth-test
  (:require
   [blaze.auth :as auth]
   [blaze.auth-spec]
   [blaze.auth.protocols :as p]
   [blaze.http-client]
   [blaze.http-client.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.openid-auth :as openid-auth]
   [blaze.openid-auth.impl :as impl]
   [blaze.openid-auth.spec]
   [blaze.openid-auth.test-util :as otu]
   [blaze.scheduler.spec]
   [blaze.scheduler.test-util :as stu]
   [blaze.test-util :as tu]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient HttpClientMock]
   [java.security KeyPair]
   [java.time Clock Instant ZoneOffset]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private ^KeyPair key-pair (otu/key-pair))

(def ^:private now 1800000000)

(def ^:private clock
  (Clock/fixed (Instant/ofEpochSecond now) ZoneOffset/UTC))

(def ^:private issuer "http://localhost:8080")

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
        (.doReturnJSON (otu/jwks (otu/jwk key-pair))))
    http-client))

(defmethod ig/init-key ::http-client-google [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))
    (-> (.onGet http-client "http://localhost:8080/jwks")
        (.doReturnJSON (slurp (io/resource "blaze/openid_auth/google.json"))))
    http-client))

(def ^:private enc-jwk
  (otu/jwk key-pair {"kid" "enc-1" "use" "enc" "alg" "RSA-OAEP"}))

(defmethod ig/init-key ::http-client-changing-jwks [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))
    (-> (.onGet http-client "http://localhost:8080/jwks")
        (.doReturnJSON (otu/jwks enc-jwk (otu/jwk key-pair)))
        (.doReturnJSON (otu/jwks enc-jwk (otu/jwk key-pair)))
        (.doReturnJSON (otu/jwks (assoc enc-jwk "kid" "enc-2") (otu/jwk key-pair)))
        (.doReturnJSON (otu/jwks (assoc enc-jwk "kid" "enc-2") (otu/jwk key-pair))))
    http-client))

(defmethod ig/init-key ::http-client-no-jwks [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))
    (-> (.onGet http-client "http://localhost:8080/jwks")
        (.doReturnJSON (otu/jwks (otu/jwk key-pair)))
        (.doReturnJSON "{}")
        (.doReturnJSON "null")
        (.doReturnJSON "{\"error\":\"temporarily_unavailable\"}"))
    http-client))

(defmethod ig/init-key ::http-client-unusable-jwks [_ _]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))
    (-> (.onGet http-client "http://localhost:8080/jwks")
        (.doReturnJSON (otu/jwks (otu/jwk key-pair)))
        (.doReturnJSON (otu/jwks enc-jwk)))
    http-client))

(def ^:private config
  {::openid-auth/backend
   {:http-client (ig/ref ::http-client-success)
    :scheduler (ig/ref :blaze.test/manual-scheduler)
    :clock clock
    :provider-url "http://localhost:8080"
    :issuer issuer
    :audience "client-172542"}
   ::http-client-success {}
   :blaze.test/manual-scheduler {}})

(def ^:private config-not-found
  (-> (assoc-in config [::openid-auth/backend :http-client] (ig/ref ::http-client-not-found))
      (dissoc ::http-client-success)
      (assoc ::http-client-not-found {})))

(def ^:private legacy-config
  (-> (update config ::openid-auth/backend dissoc :issuer :audience)
      (assoc-in [::openid-auth/backend :legacy-mode?] true)))

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
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :provider-url))))

  (testing "missing clock"
    (given-failed-system (update config ::openid-auth/backend dissoc :clock)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))))

  (testing "invalid http-client"
    (given-failed-system (assoc-in config [::openid-auth/backend :http-client] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/http-client]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid scheduler"
    (given-failed-system (assoc-in config [::openid-auth/backend :scheduler] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/scheduler]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [::openid-auth/backend :clock] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid provider-url"
    (given-failed-system (assoc-in config [::openid-auth/backend :provider-url] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::openid-auth/provider-url]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid issuer"
    (doseq [issuer [::invalid "a\"b" "a\\b" "a\nb" ""]]
      (given-failed-system (assoc-in config [::openid-auth/backend :issuer] issuer)
        :key := ::openid-auth/backend
        :reason := ::ig/build-failed-spec
        [:cause-data ::s/problems 0 :via 0] := ::openid-auth/issuer
        [:cause-data ::s/problems 0 :val] := issuer)))

  (testing "invalid audience"
    (given-failed-system (assoc-in config [::openid-auth/backend :audience] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::openid-auth/audience]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid legacy-mode?"
    (given-failed-system (assoc-in config [::openid-auth/backend :legacy-mode?] ::invalid)
      :key := ::openid-auth/backend
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::openid-auth/legacy-mode?]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "RFC mode"
    (testing "without issuer"
      (doseq [config [(update config ::openid-auth/backend dissoc :issuer)
                      (-> (assoc-in config [::openid-auth/backend :issuer] nil)
                          (assoc-in [::openid-auth/backend :legacy-mode?] false))]]
        (given-failed-system config
          :key := ::openid-auth/backend
          :reason := ::ig/build-threw-exception
          [:cause-data ::anom/category] := ::anom/incorrect
          [:cause-data ::anom/message] := "OPENID_PROVIDER_ISSUER is not set. In RFC mode, both OPENID_PROVIDER_ISSUER and OPENID_AUDIENCE are required. Set them, or set OPENID_LEGACY_MODE=true to keep the deprecated legacy validation.")))

    (testing "without audience"
      (given-failed-system (update config ::openid-auth/backend dissoc :audience)
        :key := ::openid-auth/backend
        :reason := ::ig/build-threw-exception
        [:cause-data ::anom/category] := ::anom/incorrect
        [:cause-data ::anom/message] := "OPENID_AUDIENCE is not set. In RFC mode, both OPENID_PROVIDER_ISSUER and OPENID_AUDIENCE are required. Set them, or set OPENID_LEGACY_MODE=true to keep the deprecated legacy validation."))

    (testing "without both"
      (given-failed-system (-> (update config ::openid-auth/backend dissoc :issuer :audience)
                               (assoc-in [::openid-auth/backend :legacy-mode?] false))
        :key := ::openid-auth/backend
        :reason := ::ig/build-threw-exception
        [:cause-data ::anom/category] := ::anom/incorrect
        [:cause-data ::anom/message] := "OPENID_PROVIDER_ISSUER and OPENID_AUDIENCE are not set. In RFC mode, both OPENID_PROVIDER_ISSUER and OPENID_AUDIENCE are required. Set them, or set OPENID_LEGACY_MODE=true to keep the deprecated legacy validation."))

    (testing "with issuer and audience"
      (with-system [{::openid-auth/keys [backend]} config]
        (is (satisfies? p/AuthenticationBackend backend)))))

  (testing "legacy mode"
    (doseq [config [legacy-config
                    (assoc-in legacy-config [::openid-auth/backend :issuer] issuer)
                    (assoc-in legacy-config [::openid-auth/backend :audience] "client-172542")
                    (assoc-in config [::openid-auth/backend :legacy-mode?] true)]]
      (with-system [{::openid-auth/keys [backend]} config]
        (is (satisfies? p/AuthenticationBackend backend))))))

(defn- logged-warnings [config]
  (let [warnings (atom [])]
    (log/with-merged-config
      {:appenders {:test {:enabled? true :min-level :warn
                          :fn #(swap! warnings conj (force (:msg_ %)))}}}
      (with-system [_ config]))
    @warnings))

(deftest startup-warnings-test
  (testing "legacy mode without issuer and audience"
    (is (= ["OPENID_LEGACY_MODE is enabled. The legacy mode is deprecated and planned for removal in the next major version."
            "OPENID_PROVIDER_ISSUER is not set; issuer (iss) claim will not be validated."
            "OPENID_AUDIENCE is not set; audience (aud) claim will not be validated."]
           (logged-warnings legacy-config))))

  (testing "legacy mode without audience"
    (is (= ["OPENID_LEGACY_MODE is enabled. The legacy mode is deprecated and planned for removal in the next major version."
            "OPENID_AUDIENCE is not set; audience (aud) claim will not be validated."]
           (logged-warnings (assoc-in legacy-config [::openid-auth/backend :issuer] issuer)))))

  (testing "legacy mode without issuer"
    (is (= ["OPENID_LEGACY_MODE is enabled. The legacy mode is deprecated and planned for removal in the next major version."
            "OPENID_PROVIDER_ISSUER is not set; issuer (iss) claim will not be validated."]
           (logged-warnings (assoc-in legacy-config [::openid-auth/backend :audience] "client-172542")))))

  (testing "RFC mode"
    (is (empty? (logged-warnings config)))))

(def ^:private claims
  {"sub" "user-184611" "iss" issuer "aud" "client-172542" "exp" (+ now 300)})

(defn- request [token]
  {:headers {"authorization" (str "Bearer " token)}})

(deftest skipped-keys-logging-test
  (let [warnings (atom [])
        warn-ticks (fn [manual-scheduler]
                     (reset! warnings [])
                     (stu/tick! manual-scheduler)
                     (filterv #(str/starts-with? % "Skip JWK") @warnings))]
    (log/with-merged-config
      {:appenders {:test {:enabled? true :min-level :warn
                          :fn #(swap! warnings conj (force (:msg_ %)))}}}
      (with-system [{:blaze.test/keys [manual-scheduler]}
                    (-> (assoc-in config [::openid-auth/backend :http-client] (ig/ref ::http-client-changing-jwks))
                        (dissoc ::http-client-success)
                        (assoc ::http-client-changing-jwks {}))]
        (testing "the first fetch warns"
          (is (= ["Skip JWK with kid \"enc-1\": `use` isn't `sig`."] (warn-ticks manual-scheduler))))

        (testing "an unchanged JWKS doesn't warn again"
          (is (= [] (warn-ticks manual-scheduler))))

        (testing "a changed JWKS warns"
          (is (= ["Skip JWK with kid \"enc-2\": `use` isn't `sig`."] (warn-ticks manual-scheduler))))

        (testing "and doesn't warn again"
          (is (= [] (warn-ticks manual-scheduler))))))))

(deftest backend-test
  (testing "before the first fetch, authentication is unavailable"
    (with-system [{::openid-auth/keys [backend]} config]
      (given (auth/authenticate backend (request (otu/token key-pair claims)))
        ::anom/category := ::anom/unavailable
        :blaze.auth/realm := issuer)

      (testing "but a request without credentials is unauthenticated"
        (given (auth/authenticate backend {})
          ::anom/category := ::anom/incorrect))))

  (testing "public key not found"
    (with-system [{::openid-auth/keys [backend]
                   :blaze.test/keys [manual-scheduler]} config-not-found]
      (stu/tick! manual-scheduler)
      (given (auth/authenticate backend (request (otu/token key-pair claims)))
        ::anom/category := ::anom/unavailable
        :blaze.auth/realm := issuer
        :blaze.auth/error := nil)))

  (testing "public key found"
    (with-system [{::openid-auth/keys [backend]
                   :blaze.test/keys [manual-scheduler]} config]
      (stu/tick! manual-scheduler)

      (testing "valid token"
        (given (auth/authenticate backend (request (otu/token key-pair claims)))
          :sub := "user-184611"))

      (testing "without credentials"
        (given (auth/authenticate backend {})
          ::anom/category := ::anom/incorrect
          :blaze.auth/realm := issuer))

      (testing "wrong audience"
        (given (auth/authenticate backend (request (otu/token key-pair (assoc claims "aud" "other"))))
          ::anom/category := ::anom/forbidden
          :blaze.auth/error := "invalid_token"))

      (testing "expired token"
        (given (auth/authenticate backend (request (otu/token key-pair (assoc claims "exp" (- now 60)))))
          ::anom/category := ::anom/forbidden))))

  (testing "a failing fetch keeps the previous keys"
    (with-system [{::openid-auth/keys [backend]
                   :blaze.test/keys [manual-scheduler]} config]
      (stu/tick! manual-scheduler)
      (with-redefs [impl/fetch-public-keys
                    (fn [_ _] {::anom/category ::anom/fault ::anom/message "msg-185012"})]
        (stu/tick! manual-scheduler))
      (given (auth/authenticate backend (request (otu/token key-pair claims)))
        :sub := "user-184611")))

  (testing "a JWKS without usable key removes the previous keys"
    (with-system [{::openid-auth/keys [backend]
                   :blaze.test/keys [manual-scheduler]}
                  (-> (assoc-in config [::openid-auth/backend :http-client] (ig/ref ::http-client-unusable-jwks))
                      (dissoc ::http-client-success)
                      (assoc ::http-client-unusable-jwks {}))]
      (stu/tick! manual-scheduler)
      (given (auth/authenticate backend (request (otu/token key-pair claims)))
        :sub := "user-184611")

      (stu/tick! manual-scheduler)
      (given (auth/authenticate backend (request (otu/token key-pair claims)))
        ::anom/category := ::anom/unavailable
        ::anom/message := "No public key of the OpenID provider is available."
        :blaze.auth/realm := issuer)))

  (testing "a document that isn't a JWKS keeps the previous keys"
    (with-system [{::openid-auth/keys [backend]
                   :blaze.test/keys [manual-scheduler]}
                  (-> (assoc-in config [::openid-auth/backend :http-client] (ig/ref ::http-client-no-jwks))
                      (dissoc ::http-client-success)
                      (assoc ::http-client-no-jwks {}))]
      (dotimes [_ 4]
        (stu/tick! manual-scheduler)
        (given (auth/authenticate backend (request (otu/token key-pair claims)))
          :sub := "user-184611"))))

  (testing "Google keys don't verify our tokens"
    (with-system [{::openid-auth/keys [backend]
                   :blaze.test/keys [manual-scheduler]}
                  (-> (assoc-in config [::openid-auth/backend :http-client] (ig/ref ::http-client-google))
                      (dissoc ::http-client-success)
                      (assoc ::http-client-google {}))]
      (stu/tick! manual-scheduler)
      (given (auth/authenticate backend (request (otu/token key-pair claims)))
        ::anom/category := ::anom/forbidden)))

  (testing "legacy mode without issuer"
    (with-system [{::openid-auth/keys [backend]
                   :blaze.test/keys [manual-scheduler]} legacy-config]
      (stu/tick! manual-scheduler)

      (testing "accepts tokens without exp and with any iss and aud"
        (given (auth/authenticate backend (request (otu/token key-pair (-> (dissoc claims "exp")
                                                                           (assoc "iss" "x" "aud" "y")))))
          :sub := "user-184611"))

      (testing "has the realm Blaze"
        (given (auth/authenticate backend {})
          :blaze.auth/realm := "Blaze")))))

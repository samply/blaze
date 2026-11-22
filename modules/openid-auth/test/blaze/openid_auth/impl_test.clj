(ns blaze.openid-auth.impl-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.auth :as auth]
   [blaze.auth-spec]
   [blaze.openid-auth.impl :as impl]
   [blaze.openid-auth.jwk :as jwk]
   [blaze.openid-auth.test-util :as otu]
   [blaze.test-util :as tu]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient HttpClientMock]
   [java.io IOException]
   [java.security KeyPair]
   [java.security.interfaces RSAPublicKey]
   [java.time Clock Instant ZoneOffset]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- padded
  "Returns the ASCII JSON document `json` padded with trailing whitespace to
  exactly `n` bytes."
  [json n]
  (str json (str/join (repeat (- n (count json)) " "))))

(defn- http-client [openid-config jwks]
  (let [http-client (HttpClientMock.)]
    (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
        (.doReturnJSON openid-config))
    (-> (.onGet http-client "http://localhost:8080/jwks")
        (.doReturnJSON jwks))
    http-client))

(def ^:private ^KeyPair key-pair (otu/key-pair))

(deftest fetch-public-key-test
  (testing "exception"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doThrowException (IOException. "msg-171306")))

      (given (impl/fetch-public-keys http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID configuration: msg-171306")))

  (testing "exception with cause"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doThrowException (IOException. "msg-171306" (IOException. "cause-msg-172519"))))

      (given (impl/fetch-public-keys http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID configuration: msg-171306: cause-msg-172519")))

  (testing "not found"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnStatus 404))

      (given (impl/fetch-public-keys http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID configuration: status: 404")))

  (testing "no JWKS URI"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{}"))

      (given (impl/fetch-public-keys http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Missing `jwks_uri` in OpenID config at `http://localhost:8080/.well-known/openid-configuration`.")))

  (testing "JWKS URI not found"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

      (-> (.onGet http-client "http://localhost:8080/jwks")
          (.doReturnStatus 404))

      (given (impl/fetch-public-keys http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the JWKS document: status: 404")))

  (testing "no key"
    (given (impl/fetch-public-keys
            (http-client "{\"jwks_uri\":\"http://localhost:8080/jwks\"}" "{\"keys\":[]}")
            "http://localhost:8080")
      ::anom/category := ::anom/fault
      ::anom/message := "Missing key in JWKS document config at `http://localhost:8080/jwks`."
      :blaze.openid-auth/skipped := []))

  (testing "a document that isn't a JWKS"
    (doseq [document ["{}" "null" "[]" "{\"error\":\"invalid_request\"}" "{\"keys\":{}}"]]
      (given (impl/fetch-public-keys
              (http-client "{\"jwks_uri\":\"http://localhost:8080/jwks\"}" document)
              "http://localhost:8080")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Error while fetching the JWKS document: The document isn't a JWK Set, which requires a `keys` array of JSON objects."
        :blaze.openid-auth/skipped := nil)))

  (testing "only unusable keys"
    (given (impl/fetch-public-keys
            (http-client "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"
                         (otu/jwks (otu/jwk key-pair {"kid" "enc-1" "use" "enc"})))
            "http://localhost:8080")
      ::anom/category := ::anom/fault
      ::anom/message := "Missing key in JWKS document config at `http://localhost:8080/jwks`."
      :blaze.openid-auth/skipped := ["Skip JWK with kid \"enc-1\": `use` isn't `sig`."]))

  (testing "skipped keys are returned"
    (given (impl/fetch-public-keys
            (http-client "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"
                         (otu/jwks (otu/jwk key-pair {"kid" "enc-1" "use" "enc"}) (otu/jwk key-pair)))
            "http://localhost:8080")
      [:keys count] := 1
      :skipped := ["Skip JWK with kid \"enc-1\": `use` isn't `sig`."]))

  (testing "jwks_uri isn't a string"
    (given (impl/fetch-public-keys (http-client "{\"jwks_uri\":1}" "{}") "http://localhost:8080")
      ::anom/category := ::anom/fault
      ::anom/message := "Missing `jwks_uri` in OpenID config at `http://localhost:8080/.well-known/openid-configuration`."))

  (testing "OpenID configuration isn't an object"
    (given (impl/fetch-public-keys (http-client "[]" "{}") "http://localhost:8080")
      ::anom/category := ::anom/fault
      ::anom/message :# "Missing `jwks_uri`.*"))

  (testing "invalid OpenID configuration"
    (given (impl/fetch-public-keys (http-client "{\"a\":1,\"a\":1}" "{}") "http://localhost:8080")
      ::anom/category := ::anom/incorrect
      ::anom/message :# "(?s)Error while fetching the OpenID configuration: Duplicate field 'a'.*"))

  (testing "OpenID configuration of 1 MiB"
    (let [base "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"
          config (padded base 1048576)]
      (is (= 1048576 (count config)))
      (given (impl/fetch-public-keys (http-client config (otu/jwks (otu/jwk key-pair))) "http://localhost:8080")
        [:keys count] := 1)

      (testing "and one byte more"
        (given (impl/fetch-public-keys (http-client (padded base 1048577) (otu/jwks (otu/jwk key-pair))) "http://localhost:8080")
          ::anom/category := ::anom/incorrect
          ::anom/message :# "(?s)Error while fetching the OpenID configuration: .*Document length.*"))))

  (testing "JWKS of 1 MiB"
    (let [base (otu/jwks (otu/jwk key-pair))
          jwks (padded base 1048576)
          config "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"]
      (is (= 1048576 (count jwks)))
      (given (impl/fetch-public-keys (http-client config jwks) "http://localhost:8080")
        [:keys count] := 1)

      (testing "and one byte more"
        (given (impl/fetch-public-keys (http-client config (padded base 1048577)) "http://localhost:8080")
          ::anom/category := ::anom/incorrect
          ::anom/message :# "(?s)Error while fetching the JWKS document: .*Document length.*"))))

  (testing "JWKS with constraint violations"
    (let [config "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"
          jwk (otu/jwk key-pair)]
      (doseq [[jwks msg] [[(otu/jwks (assoc jwk "x" [[[[[{"a" 1}]]]]])) ".*nesting depth.*"]
                          [(str/replace (otu/jwks jwk) "\"e\":" "\"n\":\"AQAB\",\"e\":") "Duplicate field 'n'.*"]
                          [(otu/jwks (assoc jwk "x5c" [(str/join (repeat 16385 "a"))])) ".*String value length.*"]]]
        (given (impl/fetch-public-keys (http-client config jwks) "http://localhost:8080")
          ::anom/category := ::anom/incorrect
          ::anom/message :# (str "(?s)Error while fetching the JWKS document: " msg)))

      (testing "at the limits"
        (given (impl/fetch-public-keys
                (http-client config (otu/jwks (assoc jwk "x" [[[[{"a" 1}]]]]
                                                     "x5c" [(str/join (repeat 16384 "a"))])))
                "http://localhost:8080")
          [:keys count] := 1))))

  (testing "returns one PublicKey"
    (doseq [[doc num]
            [[(slurp (io/resource "blaze/openid_auth/jwks-one-key.json")) 1]
             [(slurp (io/resource "blaze/openid_auth/jwks-one-sig-and-one-enc-key.json")) 1]
             [(slurp (io/resource "blaze/openid_auth/google.json")) 2]
             [(slurp (io/resource "blaze/openid_auth/jwks-keycloak-26.json")) 1]]]
      (let [http-client (HttpClientMock.)]
        (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
            (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

        (-> (.onGet http-client "http://localhost:8080/jwks")
            (.doReturnJSON doc))

        (given (impl/fetch-public-keys http-client "http://localhost:8080")
          [:keys count] := num
          [:keys 0 :key] :? #(instance? RSAPublicKey %))))))

(def ^:private now 1800000000)

(def ^:private clock
  (Clock/fixed (Instant/ofEpochSecond now) ZoneOffset/UTC))

(def ^:private issuer "https://keycloak.example.com/realms/blaze")

(def ^:private public-keys
  (:keys (jwk/public-keys (j/read-value (otu/jwks (otu/jwk key-pair)) j/keyword-keys-object-mapper))))

(defn- backend
  ([opts] (backend opts public-keys))
  ([opts public-keys]
   (impl/->Backend nil (atom {:keys public-keys}) clock opts (or (:issuer opts) "Blaze"))))

(def ^:private rfc-opts {:legacy-mode? false :issuer issuer :audience "blaze"})

(def ^:private claims
  {"sub" "user-183012" "iss" issuer "aud" "blaze" "exp" (+ now 300)})

(defn- request [token]
  {:headers {"authorization" (str "Bearer " token)}})

(deftest backend-test
  (testing "valid token"
    (given (auth/authenticate (backend rfc-opts) (request (otu/token key-pair claims)))
      :sub := "user-183012"))

  (testing "rejected token"
    (given (auth/authenticate (backend rfc-opts) (request (otu/token key-pair (dissoc claims "exp"))))
      ::anom/category := ::anom/forbidden
      :blaze.auth/realm := issuer
      :blaze.auth/error := "invalid_token"))

  (testing "malformed token"
    (given (auth/authenticate (backend rfc-opts) (request "a.b"))
      ::anom/category := ::anom/forbidden
      ::anom/message := "Invalid bearer token."
      :blaze.auth/realm := issuer
      :blaze.auth/error := "invalid_token"))

  (testing "legacy mode without issuer has the realm Blaze"
    (given (auth/authenticate (backend {:legacy-mode? true}) (request "a.b"))
      ::anom/category := ::anom/forbidden
      :blaze.auth/realm := "Blaze"
      :blaze.auth/error := "invalid_token"))

  (testing "no public key is available"
    (doseq [public-keys [nil []]]
      (given (auth/authenticate (backend rfc-opts public-keys) (request (otu/token key-pair claims)))
        ::anom/category := ::anom/unavailable
        ::anom/message := "No public key of the OpenID provider is available."
        :blaze.auth/realm := issuer
        :blaze.auth/error := nil))

    (testing "a request without credentials is still unauthenticated"
      (given (auth/authenticate (backend rfc-opts []) {})
        ::anom/category := ::anom/incorrect)))

  (testing "without credentials"
    (doseq [request [{} {:headers {}} {:headers {"authorization" "Basic foo"}}
                     {:headers {"authorization" "Bearer "}}
                     {:headers {"authorization" "bearer foo"}}]]
      (given (auth/authenticate (backend rfc-opts) request)
        ::anom/category := ::anom/incorrect
        :blaze.auth/realm := issuer
        :blaze.auth/error := nil))))

(deftest key-state-test
  (testing "a successful fetch replaces the state"
    (is (= {:keys public-keys :skipped ["a"]}
           (impl/key-state {:keys [] :skipped ["b"]} {:keys public-keys :skipped ["a"]}))))

  (testing "a failing fetch keeps the keys"
    (is (= {:keys public-keys :skipped ["b"]}
           (impl/key-state {:keys public-keys :skipped ["b"]} (ba/fault "msg-115211")))))

  (testing "a JWKS without usable key removes the keys and updates the skipped keys"
    (is (= {:keys [] :skipped ["a"]}
           (impl/key-state {:keys public-keys :skipped ["b"]}
                           (ba/fault "msg-115211" :blaze.openid-auth/skipped ["a"]))))

    (testing "also an empty JWKS"
      (is (= {:keys [] :skipped []}
             (impl/key-state {:keys public-keys :skipped ["b"]}
                             (ba/fault "msg-115211" :blaze.openid-auth/skipped []))))))

  (testing "the initial state"
    (is (= {:keys public-keys :skipped []}
           (impl/key-state nil {:keys public-keys :skipped []})))
    (is (nil? (impl/key-state nil (ba/fault "msg-115211"))))))

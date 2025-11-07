(ns blaze.openid-auth.impl-test
  (:require
   [blaze.openid-auth.impl :as impl]
   [blaze.test-util :as tu]
   [buddy.auth.middleware :as middleware]
   [buddy.sign.jwt :as jwt]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient HttpClientMock]
   [java.io IOException]
   [java.security PublicKey]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

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
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

      (-> (.onGet http-client "http://localhost:8080/jwks")
          (.doReturnJSON "{}"))

      (given (impl/fetch-public-keys http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Missing key in JWKS document config at `http://localhost:8080/jwks`.")))

  (testing "returns one PublicKey"
    (doseq [[doc num]
            [[(slurp (io/resource "blaze/openid_auth/jwks-one-key.json")) 1]
             [(slurp (io/resource "blaze/openid_auth/jwks-one-sig-and-one-enc-key.json")) 1]
             [(slurp (io/resource "blaze/openid_auth/google.json")) 2]]]
      (let [http-client (HttpClientMock.)]
        (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
            (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

        (-> (.onGet http-client "http://localhost:8080/jwks")
            (.doReturnJSON doc))

        (given (impl/fetch-public-keys http-client "http://localhost:8080")
          count := num
          identity :∀ #(instance? PublicKey %))))))

(deftest backend-test
  (testing "successful with one public key"
    (let [backend (impl/->Backend nil (atom [::public-key]))]
      (with-redefs
       [jwt/unsign
        (fn [token key opts]
          (is (= "token-184940" token))
          (is (= ::public-key key))
          (is (= {:alg :rs256} opts))
          ::unsigned-token)]
        (is (= ::unsigned-token
               (middleware/authenticate-request
                {:headers {"authorization" "Bearer token-184940"}}
                [backend]))))))

  (testing "successful at second public key"
    (let [backend (impl/->Backend nil (atom [::public-key-0 ::public-key-1]))]
      (with-redefs
       [jwt/unsign
        (fn [token key opts]
          (is (= "token-184940" token))
          (is (= {:alg :rs256} opts))
          (if (= ::public-key-1 key)
            ::unsigned-token
            (throw (ex-info "" {:type :validation}))))]
        (is (= ::unsigned-token
               (middleware/authenticate-request
                {:headers {"authorization" "Bearer token-184940"}}
                [backend]))))))

  (testing "basic authorization header"
    (let [backend (impl/->Backend nil (atom [::public-key]))]
      (is (nil? (middleware/authenticate-request
                 {:headers {"authorization" "Basic foo"}} [backend])))))

  (testing "no authorization header"
    (let [backend (impl/->Backend nil (atom [::public-key]))]
      (is (nil? (middleware/authenticate-request {:headers {}} [backend])))))

  (testing "no headers"
    (let [backend (impl/->Backend nil (atom [::public-key]))]
      (is (nil? (middleware/authenticate-request {} [backend])))))

  (testing "unsign error"
    (let [backend (impl/->Backend nil (atom [::public-key]))]
      (with-redefs
       [jwt/unsign
        (fn [_ _ _]
          (throw (Exception.)))]
        (is (nil? (middleware/authenticate-request
                   {:headers {"authorization" "Bearer token-184940"}}
                   [backend]))))))

  (testing "no public key"
    (let [backend (impl/->Backend nil (atom nil))]
      (is (nil? (middleware/authenticate-request
                 {:headers {"authorization" "Bearer token-184940"}}
                 [backend]))))))

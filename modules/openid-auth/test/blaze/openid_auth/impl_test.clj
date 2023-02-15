(ns blaze.openid-auth.impl-test
  (:require
    [blaze.openid-auth.impl :as impl]
    [blaze.test-util :as tu]
    [buddy.auth.middleware :as middleware]
    [buddy.sign.jwt :as jwt]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [com.pgssoft.httpclient HttpClientMock]
    [java.security PublicKey]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


;; The following json has been taken from https://samples.auth0.com/.well-known/jwks.json
(def jwks-document "{\"keys\":[{\"alg\":\"RS256\",\"kty\":\"RSA\",\"use\":\"sig\",\"x5c\":[\"MIIDCzCCAfOgAwIBAgIJAJP6qydiMpsuMA0GCSqGSIb3DQEBBQUAMBwxGjAYBgNVBAMMEXNhbXBsZXMuYXV0aDAuY29tMB4XDTE0MDUyNjIyMDA1MFoXDTI4MDIwMjIyMDA1MFowHDEaMBgGA1UEAwwRc2FtcGxlcy5hdXRoMC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCkH4CFGSJ4s3mwCBzaGGwxa9Jxzfb1ia4nUumxbsuaB7PClZZgrNQiOR3MXVNV9W6F1D+wjT6oFHOo7TOkVI22I/ff3XZTE0F35UUHGWRtiQ4LdZxwOPTed2Lax3F2DEyl3Y0CguUKbq2sSghvHYcggM6aj3N53VBsnBh/kdrURDLx1RYqBIL6Fvkhb/V/v/u9UKhZM0CDQRef9FZ7R8q9ie9cnbDOj1dT9d64kiJIYtTraG0gOrs4LI+4KK0EZu5R7Uo053IK7kfNasWhDkl8yxNYkDxwfcIuAcDmLgLnAI4tfW5beJuw+/w75PO/EwzwsnvppXaAz7e3Wf8g1yWFAgMBAAGjUDBOMB0GA1UdDgQWBBTsmytFLNox+NUZdTNlCUL3hHrngTAfBgNVHSMEGDAWgBTsmytFLNox+NUZdTNlCUL3hHrngTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBQUAA4IBAQAodbRX/34LnWB70l8dpDF1neDoG29F0XdpE9ICWHeWB1gb/FvJ5UMy9/pnL0DI3mPwkTDDob+16Zc68o6dT6sH3vEUP1iRreJlFADEmJZjrH9P4Y7ttx3G2Uw2RU5uucXIqiyMDBrQo4vx4Lnghl+b/WYbZJgzLfZLgkOEjcznS0Yi5Wdz6MvaL3FehSfweHyrjmxz0e8elHq7VY8OqRA+4PmUBce9BgDCk9fZFjgj8l0m9Vc5pPKSY9LMmTyrYkeDr/KppqdXKOCHmv7AIGb6rMCtbkIL/CM7Bh9Hx78/UKAz87Sl9A1yXVNjKbZwOEW60ORIwJmd8Tv46gJF+/rV\"],\"n\":\"pB-AhRkieLN5sAgc2hhsMWvScc329YmuJ1LpsW7LmgezwpWWYKzUIjkdzF1TVfVuhdQ_sI0-qBRzqO0zpFSNtiP33912UxNBd-VFBxlkbYkOC3WccDj03ndi2sdxdgxMpd2NAoLlCm6trEoIbx2HIIDOmo9zed1QbJwYf5Ha1EQy8dUWKgSC-hb5IW_1f7_7vVCoWTNAg0EXn_RWe0fKvYnvXJ2wzo9XU_XeuJIiSGLU62htIDq7OCyPuCitBGbuUe1KNOdyCu5HzWrFoQ5JfMsTWJA8cH3CLgHA5i4C5wCOLX1uW3ibsPv8O-TzvxMM8LJ76aV2gM-3t1n_INclhQ\",\"e\":\"AQAB\",\"kid\":\"NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg\",\"x5t\":\"NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg\"}]}")


(deftest fetch-public-key-test
  (testing "not found"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnStatus 404))

      (try
        (impl/fetch-public-key http-client "http://localhost:8080")
        (is false)
        (catch Exception e
          (given (ex-data e)
            :uri := "http://localhost:8080/.well-known/openid-configuration"
            :status := 404)))))

  (testing "no JWKS URI"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{}"))

      (try
        (impl/fetch-public-key http-client "http://localhost:8080")
        (is false)
        (catch Exception e
          (is (= "Missing `jwks_uri` in OpenID config at `http://localhost:8080/.well-known/openid-configuration`."
                 (ex-message e)))))))

  (testing "no key"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

      (-> (.onGet http-client "http://localhost:8080/jwks")
          (.doReturnJSON "{}"))

      (try
        (impl/fetch-public-key http-client "http://localhost:8080")
        (is false)
        (catch Exception e
          (is (= "Missing key in JWKS document config at `http://localhost:8080/jwks`."
                 (ex-message e)))))))

  (testing "success"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

      (-> (.onGet http-client "http://localhost:8080/jwks")
          (.doReturnJSON jwks-document))

      (is (instance? PublicKey (impl/fetch-public-key http-client "http://localhost:8080"))))))


(deftest backend-test
  (testing "successful"
    (let [backend (impl/->Backend nil (atom ::public-key))]
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

  (testing "basic authorization header"
    (let [backend (impl/->Backend nil (atom ::public-key))]
      (is (nil? (middleware/authenticate-request
                  {:headers {"authorization" "Basic foo"}} [backend])))))

  (testing "no authorization header"
    (let [backend (impl/->Backend nil (atom ::public-key))]
      (is (nil? (middleware/authenticate-request {:headers {}} [backend])))))

  (testing "no headers"
    (let [backend (impl/->Backend nil (atom ::public-key))]
      (is (nil? (middleware/authenticate-request {} [backend])))))

  (testing "unsign error"
    (let [backend (impl/->Backend nil (atom ::public-key))]
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

(ns blaze.openid-auth.impl-test
  (:require
   [blaze.openid-auth.impl :as impl]
   [blaze.test-util :as tu]
   [buddy.auth.middleware :as middleware]
   [buddy.sign.jwt :as jwt]
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

;; The following json has been taken from https://samples.auth0.com/.well-known/jwks.json
(def jwks-document-one-key
  "{\"keys\":[{\"alg\":\"RS256\",\"kty\":\"RSA\",\"use\":\"sig\",\"x5c\":[\"MIIDCzCCAfOgAwIBAgIJAJP6qydiMpsuMA0GCSqGSIb3DQEBBQUAMBwxGjAYBgNVBAMMEXNhbXBsZXMuYXV0aDAuY29tMB4XDTE0MDUyNjIyMDA1MFoXDTI4MDIwMjIyMDA1MFowHDEaMBgGA1UEAwwRc2FtcGxlcy5hdXRoMC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCkH4CFGSJ4s3mwCBzaGGwxa9Jxzfb1ia4nUumxbsuaB7PClZZgrNQiOR3MXVNV9W6F1D+wjT6oFHOo7TOkVI22I/ff3XZTE0F35UUHGWRtiQ4LdZxwOPTed2Lax3F2DEyl3Y0CguUKbq2sSghvHYcggM6aj3N53VBsnBh/kdrURDLx1RYqBIL6Fvkhb/V/v/u9UKhZM0CDQRef9FZ7R8q9ie9cnbDOj1dT9d64kiJIYtTraG0gOrs4LI+4KK0EZu5R7Uo053IK7kfNasWhDkl8yxNYkDxwfcIuAcDmLgLnAI4tfW5beJuw+/w75PO/EwzwsnvppXaAz7e3Wf8g1yWFAgMBAAGjUDBOMB0GA1UdDgQWBBTsmytFLNox+NUZdTNlCUL3hHrngTAfBgNVHSMEGDAWgBTsmytFLNox+NUZdTNlCUL3hHrngTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBQUAA4IBAQAodbRX/34LnWB70l8dpDF1neDoG29F0XdpE9ICWHeWB1gb/FvJ5UMy9/pnL0DI3mPwkTDDob+16Zc68o6dT6sH3vEUP1iRreJlFADEmJZjrH9P4Y7ttx3G2Uw2RU5uucXIqiyMDBrQo4vx4Lnghl+b/WYbZJgzLfZLgkOEjcznS0Yi5Wdz6MvaL3FehSfweHyrjmxz0e8elHq7VY8OqRA+4PmUBce9BgDCk9fZFjgj8l0m9Vc5pPKSY9LMmTyrYkeDr/KppqdXKOCHmv7AIGb6rMCtbkIL/CM7Bh9Hx78/UKAz87Sl9A1yXVNjKbZwOEW60ORIwJmd8Tv46gJF+/rV\"],\"n\":\"pB-AhRkieLN5sAgc2hhsMWvScc329YmuJ1LpsW7LmgezwpWWYKzUIjkdzF1TVfVuhdQ_sI0-qBRzqO0zpFSNtiP33912UxNBd-VFBxlkbYkOC3WccDj03ndi2sdxdgxMpd2NAoLlCm6trEoIbx2HIIDOmo9zed1QbJwYf5Ha1EQy8dUWKgSC-hb5IW_1f7_7vVCoWTNAg0EXn_RWe0fKvYnvXJ2wzo9XU_XeuJIiSGLU62htIDq7OCyPuCitBGbuUe1KNOdyCu5HzWrFoQ5JfMsTWJA8cH3CLgHA5i4C5wCOLX1uW3ibsPv8O-TzvxMM8LJ76aV2gM-3t1n_INclhQ\",\"e\":\"AQAB\",\"kid\":\"NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg\",\"x5t\":\"NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg\"}]}")

;; The following json has been taken from Keycloak
(def jwks-document-one-sig-and-one-enc-key
  "{\"keys\":[{\"kid\":\"hNjSpg83WpDK96cVRpM4wAJ6nFZ2bUqH-ITdcrDMqHk\",\"kty\":\"RSA\",\"alg\":\"RSA-OAEP\",\"use\":\"enc\",\"n\":\"gmeTilc-KnTIYXym10hOMOQpkIcF88SFAiMwm3rfsqzBWKqXVtrNkA6XgcYL8mhM6Pwh7Lp2Z7H0xQt3BX86epNn9MgHdyj_Amo1y5r5uaMjurMGaJJ72Lt5AfrrekzE850r0MgOYISl7_Q6lorOZh5ZXZYIlUn5iEFKkBm2oah8guRGM9N-sEYsioFaIFexj_qouuJNQybenr6vkOGxlDJTSsDfTAGwPcYqwVOhh6XUf2JxpGrAZ533FAaIGJccCX_IMmdasjsW7qOy0EZMeqOXXBo-rCeDl_Uzix8yCzR1R9ZRy6Y5DTHJywiTwvLZ09qUrb6jDBbSICM_ep6VDw\",\"e\":\"AQAB\",\"x5c\":[\"MIICmzCCAYMCBgGO4haZbzANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjQwNDE1MTQwNjU1WhcNMzQwNDE1MTQwODM1WjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCCZ5OKVz4qdMhhfKbXSE4w5CmQhwXzxIUCIzCbet+yrMFYqpdW2s2QDpeBxgvyaEzo/CHsunZnsfTFC3cFfzp6k2f0yAd3KP8CajXLmvm5oyO6swZoknvYu3kB+ut6TMTznSvQyA5ghKXv9DqWis5mHlldlgiVSfmIQUqQGbahqHyC5EYz036wRiyKgVogV7GP+qi64k1DJt6evq+Q4bGUMlNKwN9MAbA9xirBU6GHpdR/YnGkasBnnfcUBogYlxwJf8gyZ1qyOxbuo7LQRkx6o5dcGj6sJ4OX9TOLHzILNHVH1lHLpjkNMcnLCJPC8tnT2pStvqMMFtIgIz96npUPAgMBAAEwDQYJKoZIhvcNAQELBQADggEBABcDMepnztJypnZswoUUMPmQj51K4P7wkHnADr6wtxDHkP8dweloVRZ3F/Et+fkzNyzcB44+ofUkmQKZ65ACXQoesZPmodUwd4Mu/bhSYZD9T4Ci5mh3g6LhLbFziCPOgQ1J095SgWtEpEgW/1KMD6lmJZLUPXyqAizADeXGhMo94ImxeZYM0tuM6sJyBXkcf4LF9fRilmLKsyJ5gA5yrPyx0Ku5PUg6mMYls2s3hWls3JQdklUSfQYZVJ8lx6gFQ7qp1glELpL7kZn4lmDJpjhDmh+Kes3cxXLMmI1Lyf5VaakrxXsAA5dDoxohSbWmvn40GDRcCQ/RZnanlQ3fFd4=\"],\"x5t\":\"kiCz6TlkB_7CPlxXOSG3Qreb5tA\",\"x5t#S256\":\"hosb8_HGua61VQjUNbIt3ojGd2r7TMCnzB4IBE_r6HA\"},{\"kid\":\"F-BDiC4BFul8t-AJyzkuMrnzqdkkSRxm6_e_hep5MC0\",\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\"6UDKj3hJlRqbU46G-RI7VJig1Pru8_55H-pjXYkjeOSDdqZCsnL7_1UOqfGpr8DrTCINsFl_sWRTGnU7f-vRZUlQED-yA1YlIbccvdHaSLHbH2vX4-MCUBsROk8Q2XNqDNdk4s4Vh1-S0IsYl1QlbCIsddyQcmMihQr0sLNttHUUkjWo1OJLEbsJ_RXk3W-Ocy96EZDyiagZHE3pNn-POu1ECbbwY1FufuM2qjKb3FSBTDw4yetnRlTzPTHkKSciGWfQtt0QGAb53qPHohgPPIwOrqcub0Z5MTAXDBxRe1AITgAgWE9bDWJyH8ucQbYN-JCVIA0e1HdUYvczj144Jw\",\"e\":\"AQAB\",\"x5c\":[\"MIICmzCCAYMCBgGO4haYpTANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjQwNDE1MTQwNjU1WhcNMzQwNDE1MTQwODM1WjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDpQMqPeEmVGptTjob5EjtUmKDU+u7z/nkf6mNdiSN45IN2pkKycvv/VQ6p8amvwOtMIg2wWX+xZFMadTt/69FlSVAQP7IDViUhtxy90dpIsdsfa9fj4wJQGxE6TxDZc2oM12TizhWHX5LQixiXVCVsIix13JByYyKFCvSws220dRSSNajU4ksRuwn9FeTdb45zL3oRkPKJqBkcTek2f4867UQJtvBjUW5+4zaqMpvcVIFMPDjJ62dGVPM9MeQpJyIZZ9C23RAYBvneo8eiGA88jA6upy5vRnkxMBcMHFF7UAhOACBYT1sNYnIfy5xBtg34kJUgDR7Ud1Ri9zOPXjgnAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAIzfif7WEanZ6RAnHxJVj0b7QCh6XozLIB56+lLwibCR3vaZqeytBwlLuKKmoCxaiqPRjXb7FjQcoR+uY9inKhKd5R+4HoqtsIwXftWf5pDqGu3wTRlIjRq/rdUtOeM+pSQuiB621xY6j9i6iTKbN2K4zycSvvNAxpf0q2q+BKzd6sV0OKNFzaszfRrh0nazd5YRSs8zLxrVGaCy3TH3kZIKAL7txr06YRw/59uatWxCUTE5HKcmWqQv9RQAOD07fu27EjJgQCPijKxC+uKNizsZHnMh/ztUrQHMitbAyp2f95HjqClvriY9ck3TbNYkvkUQcuztp8Wnm1W/+ZGi178=\"],\"x5t\":\"o_K7DUtgUsR2KfEtWKUyqBvWmN4\",\"x5t#S256\":\"Q2-uhZQHHF892lbedQgogkBqJ16tAAD59Trg3amSa-Y\"}]}")

(deftest fetch-public-key-test
  (testing "exception"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doThrowException (IOException. "msg-171306")))

      (given (impl/fetch-public-key http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID configuration: msg-171306")))

  (testing "exception with cause"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doThrowException (IOException. "msg-171306" (IOException. "cause-msg-172519"))))

      (given (impl/fetch-public-key http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID configuration: msg-171306: cause-msg-172519")))

  (testing "not found"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnStatus 404))

      (given (impl/fetch-public-key http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the OpenID configuration: status: 404")))

  (testing "no JWKS URI"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{}"))

      (given (impl/fetch-public-key http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Missing `jwks_uri` in OpenID config at `http://localhost:8080/.well-known/openid-configuration`.")))

  (testing "JWKS URI not found"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

      (-> (.onGet http-client "http://localhost:8080/jwks")
          (.doReturnStatus 404))

      (given (impl/fetch-public-key http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Error while fetching the JWKS document: status: 404")))

  (testing "no key"
    (let [http-client (HttpClientMock.)]
      (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
          (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

      (-> (.onGet http-client "http://localhost:8080/jwks")
          (.doReturnJSON "{}"))

      (given (impl/fetch-public-key http-client "http://localhost:8080")
        ::anom/category := ::anom/fault
        ::anom/message := "Missing key in JWKS document config at `http://localhost:8080/jwks`.")))

  (testing "returns one PublicKey"
    (doseq [doc [jwks-document-one-key jwks-document-one-sig-and-one-enc-key]]
      (let [http-client (HttpClientMock.)]
        (-> (.onGet http-client "http://localhost:8080/.well-known/openid-configuration")
            (.doReturnJSON "{\"jwks_uri\":\"http://localhost:8080/jwks\"}"))

        (-> (.onGet http-client "http://localhost:8080/jwks")
            (.doReturnJSON doc))

        (is (instance? PublicKey (impl/fetch-public-key http-client "http://localhost:8080")))))))

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

(ns blaze.openid-auth.jwt-test
  (:require
   [blaze.openid-auth.jwk :as jwk]
   [blaze.openid-auth.jwt :as jwt]
   [blaze.openid-auth.jwt-spec]
   [blaze.openid-auth.test-util :as otu]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [jsonista.core :as j]
   [juxt.iota :refer [given]])
  (:import
   [java.security KeyPair KeyPairGenerator Signature]
   [java.security.spec ECGenParameterSpec MGF1ParameterSpec PSSParameterSpec]
   [java.util Base64]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private ^KeyPair k1 (otu/key-pair))
(def ^:private ^KeyPair k2 (otu/key-pair))
(def ^:private ^KeyPair unrelated (otu/key-pair))

(defn- keywordize [x]
  (j/read-value (j/write-value-as-string x) j/keyword-keys-object-mapper))

(defn- public-keys [& jwks]
  (:keys (jwk/public-keys {:keys (mapv keywordize jwks)})))

(def ^:private test-keys
  (public-keys (otu/jwk k1 {"kid" "k1"}) (otu/jwk k2 {"kid" "k2"})))

(def ^:private now 1800000000)
(def ^:private issuer "https://keycloak.example.com/realms/blaze")
(def ^:private audience "blaze")

(def ^:private rfc-opts {:legacy-mode? false :issuer issuer :audience audience})
(def ^:private legacy-opts {:legacy-mode? true})
(def ^:private legacy-iss-aud-opts {:legacy-mode? true :issuer issuer :audience audience})

(def ^:private claims
  {"sub" "user-171612" "iss" issuer "aud" audience "exp" (+ now 300) "iat" now})

(defn- verify [opts token]
  (jwt/verify test-keys opts now token))

(defn- token
  ([claims] (otu/token k1 claims))
  ([header claims] (otu/token k1 header claims)))

(defn- rejected? [x]
  (contains? #{::anom/incorrect ::anom/forbidden} (::anom/category x)))

(defmacro ^:private accepted-in [modes token]
  `(doseq [[mode# opts#] [[:rfc rfc-opts] [:legacy legacy-opts]]]
     (if (contains? ~modes mode#)
       (is (= "user-171612" (:sub (verify opts# ~token))) (name mode#))
       (is (rejected? (verify opts# ~token)) (name mode#)))))

(defn- segments [token]
  (str/split token #"\."))

(defn- flip-char [^String s i]
  (let [c (.charAt s i)]
    (str (subs s 0 i) (if (= \A c) \B \A) (subs s (inc i)))))

(defn- flip-byte [^bytes bs i]
  (let [bs (aclone bs)]
    (aset bs i (unchecked-byte (bit-xor (aget bs i) 1)))
    bs))

(defn- decode ^bytes [^String s]
  (.decode (Base64/getUrlDecoder) s))

(deftest valid-test
  (testing "minimal RFC claims"
    (given (verify rfc-opts (token {"sub" "user-171612" "iss" issuer "aud" audience "exp" (+ now 300)}))
      :sub := "user-171612"
      :iss := issuer))

  (testing "claims are returned with keyword keys"
    (given (verify rfc-opts (token (assoc claims "realm_access" {"roles" ["a"]})))
      [:realm_access :roles] := ["a"]))

  (testing "typ isn't checked"
    (doseq [header [{"alg" "RS256" "typ" "JWT"} {"alg" "RS256" "typ" "at+jwt"}
                    {"alg" "RS256"} {"alg" "RS256" "typ" 1}]]
      (accepted-in #{:rfc :legacy} (token header claims))))

  (testing "aud as string and as array"
    (accepted-in #{:rfc :legacy} (token (assoc claims "aud" audience)))
    (accepted-in #{:rfc :legacy} (token (assoc claims "aud" ["other" audience]))))

  (testing "kid matching one of several keys"
    (accepted-in #{:rfc :legacy} (otu/token k2 {"alg" "RS256" "kid" "k2"} claims)))

  (testing "no kid with several keys"
    (accepted-in #{:rfc :legacy} (otu/token k2 {"alg" "RS256"} claims)))

  (testing "RFC 7515 A.2"
    (let [token (str "eyJhbGciOiJSUzI1NiJ9"
                     ".eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFt"
                     "cGxlLmNvbS9pc19yb290Ijp0cnVlfQ"
                     ".cC4hiUPoj9Eetdgtv3hF80EGrhuB__dzERat0XF9g2VtQgr9PJbu3XOiZj5RZmh7"
                     "AAuHIm4Bh-0Qc_lF5YKt_O8W2Fp5jujGbds9uJdbF9CUAr7t1dnZcAcQjbKBYNX4"
                     "BAynRFdiuB--f_nZLgrnbyTyWzO75vRK5h6xBArLIARNPvkSjtQBMHlb1L07Qe7K"
                     "0GarZRmB_eSN9383LcOLn6_dO--xi12jzDwusC-eOkHWEsqtFZESc6BfI7noOPqv"
                     "hJ1phCnvWh6IeYI2w9QOYEUipUTI8np6LbgGY9Fs98rqVt5AXLIhWkWywlVmtVrB"
                     "p0igcN_IoypGlUPQGe77Rw")
          a2-keys (public-keys
                   {"kty" "RSA"
                    "n" (str "ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26mPddx"
                             "HmfHQp-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZS-pCgNMs"
                             "D1W_YpRPEwOWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-bSf63kpaSH"
                             "SXndS5z5rexMdbBYUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg665xsmtdV"
                             "MTBQY4uDZlxvb3qCo5ZwKh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmoudF8"
                             "NAco9_h9iaGNj8q2ethFkMLs91kzk2PAcDTW9gb54h4FRWyuXpoQ")
                    "e" "AQAB"})]
      (given (jwt/verify a2-keys legacy-opts 1300819379 token)
        :iss := "joe"
        :exp := 1300819380
        (keyword "http://example.com/is_root") := true)

      (testing "expired"
        (given (jwt/verify a2-keys legacy-opts 1300819380 token)
          ::anom/category := ::anom/forbidden
          ::anom/message := "Token expired.")))))

(deftest mode-differences-test
  (testing "alg"
    (doseq [header [{"alg" "rs256"} {"alg" "RS512"} {} {"alg" 1} {"alg" "none"}
                    {"alg" "HS256"}]]
      (accepted-in #{:legacy} (token header claims)))
    (given (verify rfc-opts (token {"alg" "RS512"} claims))
      ::anom/category := ::anom/forbidden
      ::anom/message := "Unsupported token algorithm."))

  (testing "crit"
    (accepted-in #{:legacy} (token {"alg" "RS256" "crit" ["exp"] "exp" 1} claims))
    (given (verify rfc-opts (token {"alg" "RS256" "crit" ["b64"]} claims))
      ::anom/category := ::anom/forbidden
      ::anom/message := "Unsupported critical token header."))

  (testing "exp missing"
    (accepted-in #{:legacy} (token (dissoc claims "exp")))
    (given (verify rfc-opts (token (dissoc claims "exp")))
      ::anom/category := ::anom/forbidden
      ::anom/message := "Missing exp claim."))

  (testing "exp and nbf null are absent in legacy mode"
    (accepted-in #{:legacy} (token (assoc claims "exp" nil)))
    (accepted-in #{:legacy} (token (assoc claims "nbf" nil)))
    (given (verify rfc-opts (token (assoc claims "exp" nil)))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid exp claim.")
    (given (verify rfc-opts (token (assoc claims "nbf" nil)))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid nbf claim."))

  (testing "iss and aud not configured in legacy mode"
    (is (= "user-171612" (:sub (verify legacy-opts (token (assoc claims "iss" "other" "aud" "other"))))))
    (is (= "user-171612" (:sub (verify legacy-opts (token (dissoc claims "iss" "aud"))))))))

(deftest time-test
  (testing "exp"
    (doseq [[exp rfc? legacy?] [[(inc now) true true]
                                [now true false]
                                [(dec now) true false]
                                [(- now 59) true false]
                                [(- now 60) false false]
                                [(- now 120) false false]]]
      (testing (str "exp = now " (- exp now))
        (accepted-in (cond-> #{} rfc? (conj :rfc) legacy? (conj :legacy))
                     (token (assoc claims "exp" exp))))))

  (testing "exp as decimal and large number"
    (accepted-in #{:rfc :legacy} (token (assoc claims "exp" (+ now 0.5))))
    (accepted-in #{:rfc :legacy} (token (assoc claims "exp" 1e20)))
    (accepted-in #{:rfc :legacy} (token (assoc claims "exp" (bigint 1e30)))))

  (testing "exp not a number"
    (doseq [exp [(str (+ now 300)) true [1]]]
      (accepted-in #{} (token (assoc claims "exp" exp))))
    (given (verify rfc-opts (token (assoc claims "exp" "1")))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid exp claim."))

  (testing "nbf"
    (doseq [[nbf rfc? legacy?] [[now true true]
                                [(inc now) true false]
                                [(+ now 60) true false]
                                [(+ now 61) false false]
                                [(+ now 120) false false]]]
      (testing (str "nbf = now + " (- nbf now))
        (accepted-in (cond-> #{} rfc? (conj :rfc) legacy? (conj :legacy))
                     (token (assoc claims "nbf" nbf))))))

  (testing "infinite numeric dates are rejected"
    (let [with-claim (fn [name value]
                       (let [claims (j/write-value-as-string (dissoc claims name))]
                         (otu/raw-token k1 "{\"alg\":\"RS256\"}"
                                        (str (subs claims 0 (dec (count claims)))
                                             ",\"" name "\":" value "}"))))]
      (doseq [value ["1e999" "-1e999"]]
        (testing (str "exp " value)
          (accepted-in #{} (with-claim "exp" value))
          (given (verify rfc-opts (with-claim "exp" value))
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid exp claim."))

        (testing (str "nbf " value)
          (accepted-in #{} (with-claim "nbf" value))
          (given (verify legacy-opts (with-claim "nbf" value))
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid nbf claim."))

        (testing (str "iat " value)
          (given (verify rfc-opts (with-claim "iat" value))
            ::anom/category := ::anom/incorrect
            ::anom/message := "Invalid iat claim.")))))

  (testing "large finite numeric dates are accepted"
    (accepted-in #{:rfc :legacy} (token (assoc claims "exp" 1e308)))
    (accepted-in #{:rfc :legacy} (token (assoc claims "nbf" -1e308 "iat" 1e308))))

  (testing "nbf not a number"
    (accepted-in #{} (token (assoc claims "nbf" "1")))
    (given (verify rfc-opts (token (assoc claims "nbf" "1")))
      ::anom/message := "Invalid nbf claim."))

  (testing "iat not a number"
    (accepted-in #{:legacy} (token (assoc claims "iat" "1")))
    (given (verify rfc-opts (token (assoc claims "iat" "1")))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid iat claim.")))

(deftest claims-test
  (doseq [opts [rfc-opts legacy-iss-aud-opts]]
    (testing (if (:legacy-mode? opts) "legacy mode with issuer and audience" "RFC mode")
      (testing "wrong iss"
        (given (verify opts (token (assoc claims "iss" "https://other")))
          ::anom/category := ::anom/forbidden
          ::anom/message := "Invalid issuer."))

      (testing "iss missing or not a string"
        (doseq [c [(dissoc claims "iss") (assoc claims "iss" 1) (assoc claims "iss" [issuer])]]
          (given (verify opts (token c))
            ::anom/message := "Invalid issuer.")))

      (testing "wrong aud"
        (given (verify opts (token (assoc claims "aud" "other")))
          ::anom/category := ::anom/forbidden
          ::anom/message := "Invalid audience."))

      (testing "aud missing, an array without the value, with a non-string or not a string"
        (doseq [c [(dissoc claims "aud") (assoc claims "aud" ["other"])
                   (assoc claims "aud" [audience 1]) (assoc claims "aud" [])
                   (assoc claims "aud" 1) (assoc claims "aud" {"a" audience})]]
          (given (verify opts (token c))
            ::anom/message := "Invalid audience."))))))

(deftest payload-test
  (doseq [payload ["[]" "\"a\"" "null" "1" "{" "{\"a\":1,\"a\":1}" "{\"a\":1} x"]]
    (testing (pr-str payload)
      (given (verify legacy-opts (otu/raw-token k1 "{\"alg\":\"RS256\"}" payload))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid token payload."))))

(deftest algorithm-confusion-test
  (testing "alg none with an empty signature is rejected by the shape check"
    (let [token (str (otu/base64url-str "{\"alg\":\"none\"}") "."
                     (otu/base64url-str (j/write-value-as-string claims)) ".")]
      (given (verify legacy-opts token)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid token.")))

  (testing "alg none with a non-empty signature"
    (let [token (str (otu/base64url-str "{\"alg\":\"none\"}") "."
                     (otu/base64url-str (j/write-value-as-string claims)) ".AAAA")]
      (accepted-in #{} token)))

  (testing "HS256 with the public key bytes as secret"
    (doseq [secret [(.getEncoded (.getPublic k1))
                    (.getBytes ^String (j/write-value-as-string (otu/jwk k1)))]]
      (let [signing-input (str (otu/base64url-str "{\"alg\":\"HS256\"}") "."
                               (otu/base64url-str (j/write-value-as-string claims)))
            token (str signing-input "." (otu/base64url (otu/hmac-bytes secret signing-input)))]
        (accepted-in #{} token))))

  (testing "PS256 signature"
    (let [signing-input (str (otu/base64url-str "{\"alg\":\"PS256\"}") "."
                             (otu/base64url-str (j/write-value-as-string claims)))
          sig (doto (Signature/getInstance "RSASSA-PSS")
                (.setParameter (PSSParameterSpec. "SHA-256" "MGF1" MGF1ParameterSpec/SHA256 32 1))
                (.initSign (.getPrivate k1))
                (.update (.getBytes signing-input)))
          token (str signing-input "." (otu/base64url (.sign sig)))]
      (accepted-in #{} token)))

  (testing "ES256 signature"
    (let [ec (.generateKeyPair (doto (KeyPairGenerator/getInstance "EC")
                                 (.initialize (ECGenParameterSpec. "secp256r1"))))
          signing-input (str (otu/base64url-str "{\"alg\":\"ES256\"}") "."
                             (otu/base64url-str (j/write-value-as-string claims)))
          sig (doto (Signature/getInstance "SHA256withECDSAinP1363Format")
                (.initSign (.getPrivate ec))
                (.update (.getBytes signing-input)))
          token (str signing-input "." (otu/base64url (.sign sig)))]
      (accepted-in #{} token))))

(deftest header-injection-test
  (testing "jwk header with the attacker's key"
    (accepted-in #{} (otu/token unrelated {"alg" "RS256" "jwk" (otu/jwk unrelated)} claims)))

  (doseq [header ["jku" "x5u"]]
    (testing header
      (accepted-in #{} (otu/token unrelated {"alg" "RS256" header "https://attacker.example/jwks"} claims))))

  (testing "x5c"
    (accepted-in #{} (otu/token unrelated {"alg" "RS256" "x5c" ["MIIB"]} claims)))

  (testing "key headers don't hurt a token signed by a known key"
    (accepted-in #{:rfc :legacy} (token {"alg" "RS256" "jwk" (otu/jwk unrelated)
                                         "jku" "https://attacker.example/jwks"
                                         "x5u" "https://attacker.example/cert"
                                         "x5c" ["MIIB"] "x5t" "a" "cty" "JWT" "foo" "bar"}
                                        claims))))

(deftest signature-test
  (testing "kid naming key A with a valid signature by key B"
    (accepted-in #{:rfc :legacy} (otu/token k2 {"alg" "RS256" "kid" "k1"} claims)))

  (testing "kid unknown to the JWKS"
    (accepted-in #{:rfc :legacy} (otu/token k2 {"alg" "RS256" "kid" "unknown"} claims)))

  (testing "kid a number"
    (accepted-in #{:rfc :legacy} (otu/token k2 {"alg" "RS256" "kid" 1} claims)))

  (testing "signature by an unrelated key"
    (given (verify rfc-opts (otu/token unrelated claims))
      ::anom/category := ::anom/forbidden
      ::anom/message := "Invalid token signature."))

  (let [[h p s] (segments (token claims))]
    (testing "a flipped byte of the signature"
      (doseq [i [0 100 255]]
        (accepted-in #{} (str h "." p "." (otu/base64url (flip-byte (decode s) i))))))

    (testing "a flipped byte of the payload"
      (accepted-in #{} (str h "." (otu/base64url (flip-byte (decode p) 10)) "." s)))

    (testing "a flipped character of the header"
      (accepted-in #{} (str (flip-char h 5) "." p "." s)))

    (testing "signature truncated and extended by one byte"
      (let [sig (decode s)]
        (accepted-in #{} (str h "." p "." (otu/base64url (java.util.Arrays/copyOf sig (dec (alength sig))))))
        (accepted-in #{} (str h "." p "." (otu/base64url (java.util.Arrays/copyOf sig (inc (alength sig))))))))

    (testing "no keys"
      (given (jwt/verify [] rfc-opts now (token claims))
        ::anom/category := ::anom/forbidden
        ::anom/message := "Invalid token signature."))))

(deftest shape-test
  (let [[h p s] (segments (token claims))
        valid (str h "." p "." s)]
    (doseq [token ["" "a" "a.b" (str h "." p) (str valid ".a") (str valid ".a.b")
                   (str "." p "." s) (str h ".." s) (str h "." p ".")
                   (str h "=." p "." s) (str valid "=") (str " " valid) (str valid " ")
                   (str h ". " p "." s) (str h "." p "." s "\n")
                   (str/replace valid #"_|-" "+") (str/replace valid #"_|-" "/")
                   (str h "." p "." s "ü")]]
      (testing (pr-str (subs token 0 (min 20 (count token))))
        (given (verify legacy-opts token)
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid token.")))))

(defn- token-of-length
  "Returns a valid token of exactly `n` characters."
  [n]
  (let [base (count (token (assoc claims "pad" "")))
        estimate (* 3 (quot (- n base) 4))]
    (first
     (for [kid-length (range 4)
           pad-length (range (- estimate 4) (+ estimate 4))
           :let [t (token {"alg" "RS256" "kid" (str/join (repeat kid-length "k"))}
                          (assoc claims "pad" (str/join (repeat pad-length "a"))))]
           :when (= n (count t))]
       t))))

(deftest size-test
  (testing "8192 characters"
    (let [t (token-of-length 8192)]
      (is (= 8192 (count t)))
      (accepted-in #{:rfc :legacy} t)))

  (testing "8193 characters"
    (let [t (token-of-length 8193)]
      (is (= 8193 (count t)))
      (given (verify rfc-opts t)
        ::anom/category := ::anom/incorrect
        ::anom/message := "Token too large."))))

(deftest header-test
  (testing "not base64url"
    (let [[_ p s] (segments (token claims))]
      (given (verify legacy-opts (str "a." p "." s))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid token header.")))

  (doseq [header ["{" "[]" "null" "\"a\"" "{\"alg\":\"RS256\",\"alg\":\"RS256\"}"
                  "{\"alg\":\"none\",\"alg\":\"RS256\"}" "{\"alg\":\"RS256\"}x"]]
    (testing (pr-str header)
      (given (verify legacy-opts (otu/raw-token k1 header (j/write-value-as-string claims)))
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid token header."))))

(defn- nested [depth]
  (str (str/join (repeat depth "{\"a\":")) "1" (str/join (repeat depth "}"))))

(deftest json-constraints-test
  (testing "nesting"
    (testing "payload of 8 levels"
      (is (map? (verify legacy-opts (otu/raw-token k1 "{\"alg\":\"RS256\"}" (nested 8))))))
    (testing "payload of 9 levels"
      (given (verify legacy-opts (otu/raw-token k1 "{\"alg\":\"RS256\"}" (nested 9)))
        ::anom/message := "Invalid token payload."))
    (testing "header of 8 levels"
      (is (map? (verify legacy-opts (otu/raw-token k1 (str "{\"alg\":\"RS256\",\"x\":" (nested 7) "}") "{}")))))
    (testing "header of 9 levels"
      (given (verify legacy-opts (otu/raw-token k1 (str "{\"alg\":\"RS256\",\"x\":" (nested 8) "}") "{}"))
        ::anom/message := "Invalid token header.")))

  (testing "numbers"
    (is (map? (verify legacy-opts (otu/raw-token k1 "{\"alg\":\"RS256\"}" (format "{\"a\":%s}" (apply str (repeat 32 "1")))))))
    (given (verify legacy-opts (otu/raw-token k1 "{\"alg\":\"RS256\"}" (format "{\"a\":%s}" (apply str (repeat 33 "1")))))
      ::anom/message := "Invalid token payload."))

  (testing "names"
    (is (map? (verify legacy-opts (otu/raw-token k1 "{\"alg\":\"RS256\"}" (format "{\"%s\":1}" (apply str (repeat 256 "a")))))))
    (given (verify legacy-opts (otu/raw-token k1 "{\"alg\":\"RS256\"}" (format "{\"%s\":1}" (apply str (repeat 257 "a")))))
      ::anom/message := "Invalid token payload."))

  (testing "a Keycloak 26 access token"
    (let [claims (assoc claims
                        "typ" "Bearer" "azp" "frontend" "sid" "8f1e" "acr" "1"
                        "allowed-origins" ["http://localhost:3000"]
                        "realm_access" {"roles" ["offline_access" "uma_authorization"]}
                        "resource_access" {"account" {"roles" ["manage-account" "view-profile"]}}
                        "authorization" {"permissions" [{"rsid" "a" "rsname" "b" "scopes" ["read"]
                                                         "claims" {"x" ["y"]}}]}
                        "scope" "openid profile email" "email_verified" false
                        "preferred_username" "john")]
      (given (verify rfc-opts (token claims))
        [:resource_access :account :roles] := ["manage-account" "view-profile"]))))

(ns blaze.openid-auth.jwk-test
  (:require
   [blaze.openid-auth.jwk :as jwk]
   [blaze.openid-auth.jwk-spec]
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
   [java.math BigInteger]
   [java.security KeyPair]
   [java.security.interfaces RSAPublicKey]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private ^KeyPair key-pair (otu/key-pair))

(defn- keywordize [jwk]
  (j/read-value (j/write-value-as-string jwk) j/keyword-keys-object-mapper))

(defn- public-keys [& jwks]
  (:keys (jwk/public-keys {:keys (mapv keywordize jwks)})))

(defn- skipped [& jwks]
  (:skipped (jwk/public-keys {:keys (mapv keywordize jwks)})))

(defn- modulus
  "Returns an odd modulus of exactly `bits` bits. It's no product of two
  primes, which the key loading doesn't check."
  [bits]
  (-> BigInteger/ONE (.shiftLeft (dec bits)) (.setBit 0) (.setBit 1)))

(defn- jwk-with [more]
  (otu/jwk key-pair more))

(defn- modulus-bits [{:keys [^RSAPublicKey key]}]
  (.bitLength (.getModulus key)))

(deftest public-keys-test
  (testing "a valid key"
    (given (public-keys (jwk-with {"kid" "kid-180812"}))
      count := 1
      [0 :kid] := "kid-180812"
      [0 :key] :? #(instance? RSAPublicKey %)
      [0 :key #(.getModulus ^RSAPublicKey %)] := (.getModulus ^RSAPublicKey (.getPublic key-pair))))

  (testing "a kid that isn't a string is dropped"
    (given (public-keys (jwk-with {"kid" 1}))
      count := 1
      [0 :kid] := nil))

  (testing "use"
    (is (= 1 (count (public-keys (jwk-with {"use" "sig"})))))
    (is (= 1 (count (public-keys (dissoc (jwk-with {}) "use")))))
    (is (empty? (public-keys (jwk-with {"use" "enc"})))))

  (testing "alg"
    (is (= 1 (count (public-keys (jwk-with {"alg" "RS256"})))))
    (is (= 1 (count (public-keys (dissoc (jwk-with {}) "alg")))))
    (is (empty? (public-keys (jwk-with {"alg" "RSA-OAEP"}))))
    (is (empty? (public-keys (jwk-with {"alg" "RS512"})))))

  (testing "key_ops"
    (is (= 1 (count (public-keys (jwk-with {"key_ops" ["verify"]})))))
    (is (= 1 (count (public-keys (jwk-with {"key_ops" ["sign" "verify"]})))))
    (is (empty? (public-keys (jwk-with {"key_ops" ["encrypt"]}))))
    (is (empty? (public-keys (jwk-with {"key_ops" "verify"})))))

  (testing "kty"
    (is (empty? (public-keys (jwk-with {"kty" "EC"}))))
    (is (empty? (public-keys (jwk-with {"kty" "oct"}))))
    (is (empty? (public-keys (dissoc (jwk-with {}) "kty")))))

  (testing "n"
    (is (empty? (public-keys (dissoc (jwk-with {}) "n"))))
    (is (empty? (public-keys (jwk-with {"n" 1}))))
    (is (empty? (public-keys (jwk-with {"n" ""}))))
    (is (empty? (public-keys (jwk-with {"n" "AA"}))) "zero")
    (is (empty? (public-keys (jwk-with {"n" "a"}))) "invalid length")
    (let [n (get (jwk-with {}) "n")]
      (is (empty? (public-keys (jwk-with {"n" (str n "=")}))) "padding")
      (is (empty? (public-keys (jwk-with {"n" (str/replace n #"[_-]" "+")})))
          "standard alphabet")
      (is (empty? (public-keys (jwk-with {"n" (str " " n)}))) "whitespace")))

  (testing "modulus size"
    (doseq [[bits valid?] [[1024 false] [2047 false] [2048 true] [4096 true]
                           [8192 true] [8193 false] [8200 false]]]
      (let [jwk (jwk-with {"n" (otu/int->base64url (modulus bits))})]
        (if valid?
          (given (public-keys jwk)
            count := 1
            [0 modulus-bits] := bits)
          (is (empty? (public-keys jwk)) (str bits " bits"))))))

  (testing "a real 1024 bit key is skipped"
    (is (empty? (public-keys (otu/jwk (otu/key-pair 1024))))))

  (testing "n of 1366 characters is accepted and of 1367 not"
    (let [n (otu/int->base64url (modulus 8192))]
      (is (= 1366 (count n)))
      (is (= 1 (count (public-keys (jwk-with {"n" n})))))
      (is (empty? (public-keys (jwk-with {"n" (str "A" n)})))
          "1367 characters with a leading zero")))

  (testing "e"
    (doseq [[e valid?] [[1 false] [3 false] [65535 false] [65536 false]
                        [65537 true] [65539 true]
                        [(.subtract (.shiftLeft BigInteger/ONE 256) BigInteger/ONE) true]
                        [(.shiftLeft BigInteger/ONE 256) false]
                        [(.add (.shiftLeft BigInteger/ONE 256) BigInteger/ONE) false]]]
      (let [jwk (jwk-with {"e" (otu/int->base64url e)})]
        (is (= (if valid? 1 0) (count (public-keys jwk))) (str "e = " e)))))

  (testing "e of 43 characters is accepted and of 44 not"
    (let [e (otu/int->base64url (.subtract (.shiftLeft BigInteger/ONE 256) BigInteger/ONE))]
      (is (= 43 (count e)))
      (is (= 1 (count (public-keys (jwk-with {"e" e})))))
      (is (empty? (public-keys (jwk-with {"e" (str "A" e)}))))))

  (testing "e missing or not a string"
    (is (empty? (public-keys (dissoc (jwk-with {}) "e"))))
    (is (empty? (public-keys (jwk-with {"e" 65537})))))

  (testing "private key members"
    (doseq [member ["d" "p" "q" "dp" "dq" "qi"]]
      (is (empty? (public-keys (jwk-with {member "AQAB"}))) member)))

  (testing "usable keys are kept in order, invalid keys are skipped"
    (given (public-keys (jwk-with {"kid" "0"}) (jwk-with {"kid" "1" "use" "enc"})
                        (jwk-with {"kid" "2"}))
      count := 2
      [0 :kid] := "0"
      [1 :kid] := "2"))

  (testing "at most 16 keys are used"
    (let [jwks (mapv #(jwk-with {"kid" (str %)}) (range 17))]
      (given (apply public-keys jwks)
        count := 16
        [15 :kid] := "15"))

    (testing "skipped keys don't count"
      (let [jwks (into [(jwk-with {"kid" "enc" "use" "enc"})]
                       (map #(jwk-with {"kid" (str %)}))
                       (range 16))]
        (given (apply public-keys jwks)
          count := 16
          [0 :kid] := "0"))))

  (testing "an empty key set is a valid JWKS"
    (given (jwk/public-keys {:keys []})
      :keys := []
      :skipped := []))

  (testing "a document that isn't a JWKS is an anomaly"
    (doseq [document [{} nil [] "a" 1 {:error "invalid_request"} {:keys nil}
                      {:keys "a"} {:keys {}} {:keys ["a"]}
                      {:keys [(keywordize (jwk-with {})) 1]}
                      {:keys [(keywordize (jwk-with {})) nil]}
                      {:keys [(keywordize (jwk-with {})) []]}]]
      (given (jwk/public-keys document)
        ::anom/category := ::anom/incorrect
        ::anom/message := "The document isn't a JWK Set, which requires a `keys` array of JSON objects.")))

  (testing "existing documents"
    (doseq [[file num] [["jwks-one-key.json" 1]
                        ["jwks-one-sig-and-one-enc-key.json" 1]
                        ["google.json" 2]
                        ["jwks-keycloak-26.json" 1]]]
      (is (= num (count (:keys (jwk/public-keys
                                (j/read-value (slurp (io/resource (str "blaze/openid_auth/" file)))
                                              j/keyword-keys-object-mapper)))))
          file))))

(deftest skipped-test
  (testing "no key is skipped"
    (is (= [] (skipped (jwk-with {"kid" "a"})))))

  (testing "the messages name the kid and the reason, never key material"
    (is (= ["Skip JWK with kid \"enc-1\": `use` isn't `sig`."
            "Skip JWK with kid \"ec-1\": `kty` isn't `RSA`."
            "Skip JWK with kid nil: `alg` isn't `RS256`."
            "Skip JWK with kid \"small\": the modulus has 1024 bits, not between 2048 and 8192."]
           (skipped (jwk-with {"kid" "enc-1" "use" "enc"})
                    (jwk-with {"kid" "ec-1" "kty" "EC"})
                    (jwk-with {"alg" "RS512"})
                    (jwk-with {"kid" "small" "n" (otu/int->base64url (modulus 1024))})
                    (jwk-with {"kid" "ok"})))))

  (testing "keys beyond the 16th are skipped"
    (is (= ["Skip JWK with kid \"16\": more than 16 usable keys."]
           (apply skipped (mapv #(jwk-with {"kid" (str %)}) (range 17)))))))

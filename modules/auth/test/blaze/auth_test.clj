(ns blaze.auth-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.auth :as auth]
   [blaze.auth-spec]
   [blaze.auth.protocols :as p]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private backend
  (reify p/AuthenticationBackend
    (-authenticate [_ {:keys [token]}]
      (if (= ::token token)
        {:sub "user-145317"}
        (ba/incorrect "No token." :blaze.auth/realm "realm-145335")))))

(deftest authenticate-test
  (testing "identity"
    (given (auth/authenticate backend {:token ::token})
      :sub := "user-145317"))

  (testing "failure"
    (given (auth/authenticate backend {})
      ::anom/category := ::anom/incorrect
      :blaze.auth/realm := "realm-145335")))

(deftest authenticated?-test
  (testing "with identity"
    (is (true? (auth/authenticated? {:identity {:sub "user-145317"}}))))

  (testing "without identity"
    (is (false? (auth/authenticated? {})))
    (is (false? (auth/authenticated? {:identity nil})))))

(deftest www-authenticate-test
  (testing "no credentials"
    (is (= "Bearer realm=\"Blaze\""
           (auth/www-authenticate
            (ba/incorrect "No token." :blaze.auth/realm "Blaze")))))

  (testing "rejected credentials"
    (is (= "Bearer realm=\"https://keycloak.example.com/realms/blaze\", error=\"invalid_token\""
           (auth/www-authenticate
            (ba/forbidden "Invalid token."
                          :blaze.auth/realm "https://keycloak.example.com/realms/blaze"
                          :blaze.auth/error "invalid_token")))))

  (testing "realm with characters needing no escaping"
    (is (= "Bearer realm=\"a b,c=d;e'f/g:h?i#j[k]l@m!n$o&p(q)r*s+t~u%v|w{x}y<z>ü\""
           (auth/www-authenticate
            (ba/incorrect "No token."
                          :blaze.auth/realm "a b,c=d;e'f/g:h?i#j[k]l@m!n$o&p(q)r*s+t~u%v|w{x}y<z>ü"))))))

(deftest realm-spec-test
  (testing "valid"
    (is (s/valid? :blaze.auth/realm "https://keycloak.example.com/realms/blaze")))

  (testing "invalid"
    (doseq [realm [nil "" "a\"b" "a\\b" "a\nb" "a\tb" "a\u0000b" "a\u007fb" 1]]
      (is (not (s/valid? :blaze.auth/realm realm)) (pr-str realm)))))

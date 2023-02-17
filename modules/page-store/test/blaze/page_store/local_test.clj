(ns blaze.page-store.local-test
  (:require
    [blaze.anomaly-spec]
    [blaze.page-store :as page-store]
    [blaze.page-store-spec]
    [blaze.page-store.local]
    [blaze.test-util :as tu :refer [given-failed-future given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [cuerdas.core :as c-str]
    [integrant.core :as ig]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def system
  {:blaze.page-store/local {:secure-rng (ig/ref :blaze.test/fixed-rng)}
   :blaze.test/fixed-rng {}})


(def token (str (c-str/repeat "A" 31) "B"))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.page-store/local nil})
      :key := :blaze.page-store/local
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.page-store/local {}})
      :key := :blaze.page-store/local
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :secure-rng))))

  (testing "invalid secure random number generator"
    (given-thrown (ig/init {:blaze.page-store/local {:secure-rng ::invalid}})
      :key := :blaze.page-store/local
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "is a page store"
    (with-system [{store :blaze.page-store/local} system]
      (is (s/valid? :blaze/page-store store)))))


(deftest get-test
  (with-system [{store :blaze.page-store/local} system]
    @(page-store/put! store [["active" "true"]])

    (testing "returns the clauses stored"
      (is (= [["active" "true"]] @(page-store/get store token))))

    (testing "not-found"
      (given-failed-future (page-store/get store (c-str/repeat "B" 32))
        ::anom/category := ::anom/not-found
        ::anom/message := (format "Clauses of token `%s` not found." (c-str/repeat "B" 32))))))


(deftest put-test
  (with-system [{store :blaze.page-store/local} system]
    (testing "shall not be called with an empty list of clauses"
      (given-failed-future (page-store/put! store [])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Clauses should not be empty."))

    (testing "returns a token"
      (is (= token @(page-store/put! store [["active" "true"]]))))))

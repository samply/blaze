(ns blaze.page-store.local-test
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir.test-util :refer [given-failed-future]]
   [blaze.metrics.core :as metrics]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.page-store :as page-store]
   [blaze.page-store-spec]
   [blaze.page-store.local]
   [blaze.page-store.spec :refer [page-store?]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze.page-store/local {:secure-rng (ig/ref :blaze.test/fixed-rng)}
   :blaze.test/fixed-rng {}
   :blaze.page-store.local/collector {:page-store (ig/ref :blaze.page-store/local)}})

(def token (str (str/join (repeat 31 "A")) "B"))

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

  (testing "invalid max size"
    (given-thrown (ig/init {:blaze.page-store/local {:max-size-in-mb ::invalid}})
      :key := :blaze.page-store/local
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :secure-rng))
      [:explain ::s/problems 1 :path 0] := :max-size-in-mb
      [:explain ::s/problems 1 :pred] := `nat-int?
      [:explain ::s/problems 1 :val] := ::invalid))

  (testing "invalid expire duration"
    (given-thrown (ig/init {:blaze.page-store/local {:expire-duration ::invalid}})
      :key := :blaze.page-store/local
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :secure-rng))
      [:explain ::s/problems 1 :path 0] := :expire-duration
      [:explain ::s/problems 1 :pred] := `time/duration?
      [:explain ::s/problems 1 :val] := ::invalid))

  (testing "is a page store"
    (with-system [{store :blaze.page-store/local} config]
      (is (s/valid? :blaze/page-store store)))))

(deftest get-test
  (with-system [{store :blaze.page-store/local} config]
    @(page-store/put! store [["active" "true"]])

    (testing "returns the clauses stored"
      (is (= [["active" "true"]] @(page-store/get store token))))

    (testing "not-found"
      (given-failed-future (page-store/get store (str/join (repeat 32 "B")))
        ::anom/category := ::anom/not-found
        ::anom/message := (format "Clauses of token `%s` not found." (str/join (repeat 32 "B")))))))

(deftest put-test
  (with-system [{store :blaze.page-store/local} config]
    (testing "shall not be called with an empty list of clauses"
      (given-failed-future (page-store/put! store [])
        ::anom/category := ::anom/incorrect
        ::anom/message := "Clauses should not be empty."))

    (testing "returns a token"
      (is (= token @(page-store/put! store [["active" "true"]]))))))

(deftest collector-init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.page-store.local/collector nil})
      :key := :blaze.page-store.local/collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.page-store.local/collector {}})
      :key := :blaze.page-store.local/collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :page-store))))

  (testing "invalid page store"
    (given-thrown (ig/init {:blaze.page-store.local/collector {:page-store ::invalid}})
      :key := :blaze.page-store.local/collector
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `page-store?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "is a collector"
    (with-system [{collector :blaze.page-store.local/collector} config]
      (is (s/valid? :blaze.metrics/collector collector)))))

(deftest collector-test
  (with-system [{collector :blaze.page-store.local/collector} config]
    (let [metrics (metrics/collect collector)]

      (given metrics
        count := 1
        [0 :type] := :gauge
        [0 :name] := "blaze_page_store_estimated_size"
        [0 :samples count] := 1
        [0 :samples 0 :value] := 0.0))))

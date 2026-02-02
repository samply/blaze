(ns blaze.page-store.local-test
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir.test-util]
   [blaze.metrics.core :as metrics]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-future given-failed-system with-system]]
   [blaze.page-store :as page-store]
   [blaze.page-store-spec]
   [blaze.page-store.local :as local]
   [blaze.page-store.local.hash :as hash]
   [blaze.page-store.spec]
   [blaze.page-store.token-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze.page-store/local {}
   :blaze.test/fixed-rng {}
   :blaze.page-store.local/collector {:page-store (ig/ref :blaze.page-store/local)}})

(def token "A6E4E6D1E2ADB75120717FE913FA5EBADDF0859588A657AFF71F270775B5FEC7")

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.page-store/local nil}
      :key := :blaze.page-store/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "invalid expire duration"
    (given-failed-system {:blaze.page-store/local {:expire-duration ::invalid}}
      :key := :blaze.page-store/local
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::local/expire-duration]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "is a page store"
    (with-system [{store :blaze.page-store/local} config]
      (is (s/valid? :blaze/page-store store)))))

(defn- patient-ref [s]
  (apply str "Patient/" (repeat 64 s)))

(defn- invalidate-clause! [store clause]
  (.invalidate ^Cache (:cache store) (hash/hash-clause clause)))

(deftest get-test
  (with-system [{store :blaze.page-store/local} config]
    @(page-store/put! store [["active" "true"]])

    (testing "returns the clauses stored"
      (is (= [["active" "true"]] @(page-store/get store token))))

    (testing "not-found"
      (given-failed-future (page-store/get store (str/join (repeat 64 "A")))
        ::anom/category := ::anom/not-found
        ::anom/message := (format "Clauses of token `%s` not found." (str/join (repeat 64 "A"))))))

  (with-system [{store :blaze.page-store/local} config]
    (let [token @(page-store/put! store [["patient" (patient-ref "a")]
                                         ["active" "true"]])]

      (testing "returns the clauses stored"
        (is (= @(page-store/get store token)
               [["patient" (patient-ref "a")] ["active" "true"]])))

      (testing "not-found after one clause is invalidated"
        (invalidate-clause! store ["active" "true"])

        (given-failed-future (page-store/get store token)
          ::anom/category := ::anom/not-found
          ::anom/message := (format "Clauses of token `%s` not found." token)))))

  (with-system [{store :blaze.page-store/local} config]
    (let [token @(page-store/put! store [["patient" (patient-ref "a")]
                                         ["active" "true"]
                                         ["code" "foo"]])]

      (testing "returns the clauses stored"
        (is (= @(page-store/get store token)
               [["patient" (patient-ref "a")] ["active" "true"] ["code" "foo"]])))

      (testing "not-found after one clause is invalidated"
        (invalidate-clause! store ["patient" (patient-ref "a")])

        (given-failed-future (page-store/get store token)
          ::anom/category := ::anom/not-found
          ::anom/message := (format "Clauses of token `%s` not found." token)))))

  (testing "with disjunction"
    (with-system [{store :blaze.page-store/local} config]
      (let [token @(page-store/put! store [[["patient" (patient-ref "a")]
                                            ["active" "true"]]
                                           ["code" "foo"]])]

        (testing "returns the clauses stored"
          (is (= @(page-store/get store token)
                 [[["patient" (patient-ref "a")] ["active" "true"]]
                  ["code" "foo"]])))

        (testing "not-found after one clause is invalidated"
          (invalidate-clause! store ["patient" (patient-ref "a")])

          (given-failed-future (page-store/get store token)
            ::anom/category := ::anom/not-found
            ::anom/message := (format "Clauses of token `%s` not found." token)))))))

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
    (given-failed-system {:blaze.page-store.local/collector nil}
      :key := :blaze.page-store.local/collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.page-store.local/collector {}}
      :key := :blaze.page-store.local/collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :page-store))))

  (testing "invalid page store"
    (given-failed-system {:blaze.page-store.local/collector {:page-store ::invalid}}
      :key := :blaze.page-store.local/collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

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
        [0 :samples 0 :value] := 0.0)))

  (testing "two clauses result in 3 entries"
    (with-system [{store :blaze.page-store/local
                   collector :blaze.page-store.local/collector} config]
      @(page-store/put! store [["patient" (patient-ref "a")]
                               ["active" "true"]])

      (let [metrics (metrics/collect collector)]

        (given metrics
          count := 1
          [0 :samples 0 :value] := 3.0))))

  (testing "a second patient adds only 2 entries sharing the active clause"
    (with-system [{store :blaze.page-store/local
                   collector :blaze.page-store.local/collector} config]
      @(page-store/put! store [["patient" (patient-ref "a")]
                               ["active" "true"]])
      @(page-store/put! store [["patient" (patient-ref "b")]
                               ["active" "true"]])

      (let [metrics (metrics/collect collector)]

        (given metrics
          count := 1
          [0 :samples 0 :value] := 5.0)))))

(ns blaze.page-store.caching-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.fhir.test-util]
   [blaze.metrics.core :as metrics]
   [blaze.metrics.spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.page-store :as page-store]
   [blaze.page-store-spec]
   [blaze.page-store.backing-store :as backing-store]
   [blaze.page-store.backing-store-spec]
   [blaze.page-store.backing-store.protocols :as bsp]
   [blaze.page-store.cache-spec]
   [blaze.page-store.caching :as caching]
   [blaze.page-store.hash :as hash]
   [blaze.page-store.spec]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Cache Policy$FixedExpiration]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn- not-found [token]
  (ba/not-found (format "Clauses of token `%s` not found." token)))

(defrecord MemBackingStore [state]
  bsp/BackingStore
  (-get [_ token]
    (ac/completed-future (or (get @state token) (not-found token))))
  (-put [_ token clauses]
    (swap! state assoc token clauses)
    (ac/completed-future nil)))

(defmethod ig/init-key ::backing-store [_ _]
  (->MemBackingStore (atom {})))

(def config
  {:blaze.page-store/caching {:backing-store (ig/ref ::backing-store)}
   ::backing-store {}
   :blaze.page-store.caching/collector
   {:page-store (ig/ref :blaze.page-store/caching)}})

(defmethod ig/init-key ::failing-backing-store [_ _]
  (reify bsp/BackingStore
    (-get [_ _]
      (ac/completed-future (ba/fault "msg-101841")))
    (-put [_ _ _]
      (ac/completed-future (ba/fault "msg-101847")))))

(def config-with-failing-backing-store
  {:blaze.page-store/caching {:backing-store (ig/ref ::failing-backing-store)}
   ::failing-backing-store {}})

(def token "4A70EBA4262BCE71A9FEEBDB06B7444999B65A8897C1E46017618C8CBA035710")
(def other-token (str/join (repeat 64 "B")))

(defn- expire-duration [cache]
  (let [^Policy$FixedExpiration expiration
        (.get (.expireAfterAccess (.policy ^Cache cache)))]
    (.getExpiresAfter expiration)))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.page-store/caching nil}
      :key := :blaze.page-store/caching
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.page-store/caching {}}
      :key := :blaze.page-store/caching
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :backing-store))))

  (testing "invalid backing store"
    (given-failed-system {:blaze.page-store/caching {:backing-store ::invalid}}
      :key := :blaze.page-store/caching
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.page-store/backing-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid expire duration"
    (given-failed-system (assoc-in config [:blaze.page-store/caching :expire-duration] ::invalid)
      :key := :blaze.page-store/caching
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::caching/expire-duration]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "is a page store"
    (with-system [{store :blaze.page-store/caching} config]
      (is (s/valid? :blaze/page-store store))))

  (testing "can be referred by the super key"
    (is (isa? :blaze.page-store/caching :blaze/page-store)))

  (testing "the default expire duration is one hour"
    (with-system [{store :blaze.page-store/caching} config]
      (is (= (time/hours 1) (expire-duration (:clause-cache store))))
      (is (= (time/hours 1) (expire-duration (:token-cache store))))))

  (testing "custom expire duration"
    (with-system [{store :blaze.page-store/caching}
                  (assoc-in config [:blaze.page-store/caching :expire-duration]
                            (time/minutes 30))]
      (is (= (time/minutes 30) (expire-duration (:clause-cache store))))
      (is (= (time/minutes 30) (expire-duration (:token-cache store)))))))

(defn- clear-cache! [store]
  (.invalidateAll ^Cache (:clause-cache store))
  (.invalidateAll ^Cache (:token-cache store)))

(defn- invalidate-clause! [store clause]
  (.invalidate ^Cache (:clause-cache store) (hash/hash-clause clause)))

(defn- clear-backing-store! [backing-store]
  (reset! (:state backing-store) {}))

(deftest get-test
  (testing "returns the clauses from the cache"
    (with-system [{store :blaze.page-store/caching
                   backing-store ::backing-store} config]
      (let [token @(page-store/put! store [["active" "true"]])]
        (clear-backing-store! backing-store)

        (is (= [["active" "true"]] @(page-store/get store token))))))

  (testing "on cache miss falls through to backing store"
    (with-system [{store :blaze.page-store/caching} config]
      (let [token @(page-store/put! store [["active" "true"]])]
        (clear-cache! store)

        (is (= [["active" "true"]] @(page-store/get store token))))))

  (testing "after cache miss populates the cache for subsequent get"
    (with-system [{store :blaze.page-store/caching
                   backing-store ::backing-store} config]
      (let [token @(page-store/put! store [["active" "true"]])]
        (clear-cache! store)
        @(page-store/get store token)
        (clear-backing-store! backing-store)

        (is (= [["active" "true"]] @(page-store/get store token))))))

  (testing "with one clause evicted while the token is still cached"
    (testing "falls through to backing store"
      (with-system [{store :blaze.page-store/caching} config]
        (let [token @(page-store/put! store [["patient" "Patient/0"]
                                             ["active" "true"]])]
          (invalidate-clause! store ["active" "true"])

          (is (= [["patient" "Patient/0"] ["active" "true"]]
                 @(page-store/get store token))))))

    (testing "populates the cache for subsequent get"
      (with-system [{store :blaze.page-store/caching
                     backing-store ::backing-store} config]
        (let [token @(page-store/put! store [["patient" "Patient/0"]
                                             ["active" "true"]])]
          (invalidate-clause! store ["active" "true"])
          @(page-store/get store token)
          (clear-backing-store! backing-store)

          (is (= [["patient" "Patient/0"] ["active" "true"]]
                 @(page-store/get store token))))))

    (testing "not-found when also missing from the backing store"
      (with-system [{store :blaze.page-store/caching
                     backing-store ::backing-store} config]
        (let [token @(page-store/put! store [["patient" "Patient/0"]
                                             ["active" "true"]])]
          (invalidate-clause! store ["active" "true"])
          (clear-backing-store! backing-store)

          (given-failed-future (page-store/get store token)
            ::anom/category := ::anom/not-found
            ::anom/message := (format "Clauses of token `%s` not found." token))))))

  (testing "populates the cache under a token that isn't the hash of the clauses"
    (with-system [{store :blaze.page-store/caching
                   backing-store ::backing-store} config]
      @(backing-store/put! backing-store other-token [["active" "true"]])

      (is (= [["active" "true"]] @(page-store/get store other-token)))

      (clear-backing-store! backing-store)

      (is (= [["active" "true"]] @(page-store/get store other-token)))))

  (testing "not-found when missing from both cache and backing store"
    (with-system [{store :blaze.page-store/caching} config]
      (given-failed-future (page-store/get store other-token)
        ::anom/category := ::anom/not-found
        ::anom/message := (format "Clauses of token `%s` not found." other-token))))

  (testing "backing store error"
    (with-system [{store :blaze.page-store/caching}
                  config-with-failing-backing-store]
      (given-failed-future (page-store/get store token)
        ::anom/category := ::anom/fault
        ::anom/message := "msg-101841"))))

(deftest put-test
  (testing "writes through to backing store under the returned token"
    (with-system [{store :blaze.page-store/caching
                   backing-store ::backing-store} config]
      (let [token @(page-store/put! store [["active" "true"]])]
        (is (= [["active" "true"]] @(backing-store/get backing-store token))))))

  (testing "returns the same token as the local page store"
    (with-system [{store :blaze.page-store/caching} config]
      (is (= token @(page-store/put! store [["active" "true"]])))))

  (testing "backing store error"
    (with-system [{store :blaze.page-store/caching}
                  config-with-failing-backing-store]
      (given-failed-future (page-store/put! store [["active" "true"]])
        ::anom/category := ::anom/fault
        ::anom/message := "msg-101847"))))

(deftest collector-init-test
  (testing "nil config"
    (given-failed-system {:blaze.page-store.caching/collector nil}
      :key := :blaze.page-store.caching/collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.page-store.caching/collector {}}
      :key := :blaze.page-store.caching/collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :page-store))))

  (testing "invalid page store"
    (given-failed-system {:blaze.page-store.caching/collector {:page-store ::invalid}}
      :key := :blaze.page-store.caching/collector
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "is a collector"
    (with-system [{collector :blaze.page-store.caching/collector} config]
      (is (s/valid? :blaze.metrics/collector collector)))))

(deftest collector-test
  (with-system [{store :blaze.page-store/caching
                 collector :blaze.page-store.caching/collector} config]
    @(page-store/put! store [["patient" "Patient/0"] ["active" "true"]])

    (given (metrics/collect collector)
      count := 1
      [0 :type] := :gauge
      [0 :name] := "blaze_page_store_estimated_size"
      [0 :samples count] := 2
      [0 :samples 0 :label-names] := ["type"]
      [0 :samples 0 :label-values] := ["token"]
      [0 :samples 0 :value] := 1.0
      [0 :samples 1 :label-names] := ["type"]
      [0 :samples 1 :label-values] := ["clause"]
      [0 :samples 1 :value] := 2.0)))

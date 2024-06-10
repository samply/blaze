(ns blaze.middleware.fhir.db-test
  "White box mocking is used here because it's difficult to differentiate
  between the database value acquisition methods if one only sees the database
  value as result."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.fhir.test-util :refer [given-failed-future]]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.fhir.util-spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom])
  (:import
   [java.util.concurrent TimeUnit]))

(st/instrument)

(defn- fixture [f]
  (st/instrument)
  (st/unstrument `db/wrap-db)
  (st/unstrument `db/wrap-snapshot-db)
  (st/unstrument `db/wrap-versioned-instance-db)
  (st/unstrument `fhir-util/sync)
  (st/unstrument `d/sync)
  (st/unstrument `d/as-of)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(def timeout 100)
(def handler (comp ac/completed-future :blaze/db))

(deftest wrap-db-test
  (testing "uses existing database value"
    (is (= ::db @((db/wrap-db handler ::node timeout) {:blaze/db ::db}))))

  (testing "uses sync for database value acquisition"
    (with-redefs
     [d/sync
      (fn [node]
        (assert (= ::node node))
        (ac/completed-future ::db))]

      (is (= ::db @((db/wrap-db handler ::node timeout) {})))))

  (testing "fails on timeout"
    (with-redefs
     [d/sync
      (fn [node]
        (assert (= ::node node))
        (ac/supply-async (constantly ::db) (ac/delayed-executor 1 TimeUnit/SECONDS)))]

      (given-failed-future ((db/wrap-db handler ::node timeout) {})
        ::anom/category := ::anom/busy
        ::anom/message := "Timeout while trying to acquire the latest known database state. At least one known transaction hasn't been completed yet. Please try to lower the transaction load or increase the timeout of 100 ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often.")))

  (testing "fails on other sync error"
    (with-redefs
     [d/sync
      (fn [node]
        (assert (= ::node node))
        (ac/completed-future (ba/fault "msg-115845")))]

      (given-failed-future ((db/wrap-db handler ::node timeout) {})
        ::anom/category := ::anom/fault
        ::anom/message := "msg-115845"))))

(deftest wrap-snapshot-db-test
  (testing "uses existing database value"
    (is (= ::db @((db/wrap-snapshot-db handler ::node timeout) {:blaze/db ::db}))))

  (testing "with missing or invalid __t"
    (doseq [t ["a" "-1"]]
      (given-failed-future ((db/wrap-snapshot-db handler ::node timeout) {:params {"__t" t}})
        ::anom/category := ::anom/incorrect
        ::anom/message := (format "Missing or invalid `__t` query param `%s`." t))))

  (testing "uses __t for database value acquisition"
    (with-redefs
     [d/sync
      (fn [node t]
        (assert (= ::node node))
        (assert (= 114429 t))
        (ac/completed-future ::db))
      d/as-of
      (fn [db t]
        (assert (= ::db db))
        (assert (= 114429 t))
        ::as-of-db)]

      (is (= ::as-of-db @((db/wrap-snapshot-db handler ::node timeout) {:params {"__t" "114429"}})))))

  (testing "fails on timeout"
    (with-redefs
     [d/sync
      (fn [node t]
        (assert (= ::node node))
        (assert (= 114148 t))
        (ac/supply-async (constantly ::db) (ac/delayed-executor 1 TimeUnit/SECONDS)))]

      (given-failed-future ((db/wrap-snapshot-db handler ::node timeout) {:params {"__t" "114148"}})
        ::anom/category := ::anom/busy
        ::anom/message := "Timeout while trying to acquire the database state with t=114148. The indexer has probably fallen behind. Please try to lower the transaction load or increase the timeout of 100 ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often.")))

  (testing "fails on other sync error"
    (with-redefs
     [d/sync
      (fn [node t]
        (assert (= ::node node))
        (assert (= 114148 t))
        (ac/completed-future (ba/fault "msg-115945")))]

      (given-failed-future ((db/wrap-snapshot-db handler ::node timeout) {:params {"__t" "114148"}})
        ::anom/category := ::anom/fault
        ::anom/message := "msg-115945"))))

(deftest wrap-versioned-instance-db-test
  (testing "uses existing database value"
    (is (= ::db @((db/wrap-versioned-instance-db handler ::node timeout) {:blaze/db ::db}))))

  (testing "with missing or invalid vid"
    (doseq [vid [nil "a" "-1"]]
      (given-failed-future ((db/wrap-versioned-instance-db handler ::node timeout) {:path-params {:vid vid}})
        ::anom/category := ::anom/incorrect
        ::anom/message := (format "Resource versionId `%s` is invalid." vid)
        :fhir/issue := "value"
        :fhir/operation-outcome := "MSG_ID_INVALID")))

  (testing "uses the vid for database value acquisition"
    (with-redefs
     [d/sync
      (fn [node t]
        (assert (= ::node node))
        (assert (= 114418 t))
        (ac/completed-future ::db))
      d/as-of
      (fn [db t]
        (assert (= ::db db))
        (assert (= 114418 t))
        ::as-of-db)]

      (is (= ::as-of-db @((db/wrap-versioned-instance-db handler ::node timeout) {:path-params {:vid "114418"}})))))

  (testing "fails on timeout"
    (with-redefs
     [d/sync
      (fn [node t]
        (assert (= ::node node))
        (assert (= 213957 t))
        (ac/supply-async (constantly ::db) (ac/delayed-executor 1 TimeUnit/SECONDS)))]

      (given-failed-future ((db/wrap-versioned-instance-db handler ::node timeout) {:path-params {:vid "213957"}})
        ::anom/category := ::anom/busy
        ::anom/message := "Timeout while trying to acquire the database state with t=213957. The indexer has probably fallen behind. Please try to lower the transaction load or increase the timeout of 100 ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often.")))

  (testing "fails on other sync error"
    (with-redefs
     [d/sync
      (fn [node t]
        (assert (= ::node node))
        (assert (= 120213 t))
        (ac/completed-future (ba/fault "msg-120219")))]

      (given-failed-future ((db/wrap-versioned-instance-db handler ::node timeout) {:path-params {:vid "120213"}})
        ::anom/category := ::anom/fault
        ::anom/message := "msg-120219"))))

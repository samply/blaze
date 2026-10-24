(ns blaze.middleware.fhir.db-test
  "White box mocking is used here because it's difficult to differentiate
  between the database value acquisition methods if one only sees the database
  value as result."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.db-spec]
   [blaze.test-util :refer [given-failed-future]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]))

(st/instrument)

(defn- fixture [f]
  (st/instrument)
  (st/unstrument `db/wrap-db)
  (st/unstrument `db/wrap-snapshot-db)
  (st/unstrument `d/with-sync-db)
  (f)
  (st/unstrument))

(test/use-fixtures :each fixture)

(def handler (comp ac/completed-future :blaze/db))

(deftest wrap-db-test
  (testing "uses existing database value"
    (is (= ::db @((db/wrap-db handler ::node) {:blaze/db ::db}))))

  (testing "uses sync for database value acquisition"
    (with-redefs
     [d/with-sync-db
      (fn [node f]
        (assert (= ::node node))
        (f ::db))]

      (is (= ::db @((db/wrap-db handler ::node) {})))))

  (testing "fails on other sync error"
    (with-redefs
     [d/with-sync-db
      (fn [node _]
        (assert (= ::node node))
        (ac/completed-future (ba/fault "msg-115845")))]

      (given-failed-future ((db/wrap-db handler ::node) {})
        ::anom/category := ::anom/fault
        ::anom/message := "msg-115845"))))

(deftest wrap-snapshot-db-test
  (testing "uses existing database value"
    (is (= ::db @((db/wrap-snapshot-db handler ::node) {:blaze/db ::db}))))

  (testing "with missing params"
    (given-failed-future ((db/wrap-snapshot-db handler ::node) nil)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing or invalid `__t` paging param `null`."))

  (testing "ignores __t from params"
    (given-failed-future ((db/wrap-snapshot-db handler ::node) {:params {"__t" "114429"}})
      ::anom/category := ::anom/incorrect
      ::anom/message := "Missing or invalid `__t` paging param `null`."))

  (testing "with missing or invalid __t"
    (doseq [t ["a" "-1"]]
      (given-failed-future ((db/wrap-snapshot-db handler ::node) {:blaze/paging-params {"__t" t}})
        ::anom/category := ::anom/incorrect
        ::anom/message := (format "Missing or invalid `__t` paging param `%s`." t))))

  (testing "uses __t for database value acquisition"
    (with-redefs
     [d/with-sync-db
      (fn [node t f]
        (assert (= ::node node))
        (assert (= 114429 t))
        (f ::db))]

      (is (= ::db @((db/wrap-snapshot-db handler ::node) {:blaze/paging-params {"__t" "114429"}})))))

  (testing "fails on other sync error"
    (with-redefs
     [d/with-sync-db
      (fn [node t _]
        (assert (= ::node node))
        (assert (= 114148 t))
        (ac/completed-future (ba/fault "msg-115945")))]

      (given-failed-future ((db/wrap-snapshot-db handler ::node) {:blaze/paging-params {"__t" "114148"}})
        ::anom/category := ::anom/fault
        ::anom/message := "msg-115945"))))

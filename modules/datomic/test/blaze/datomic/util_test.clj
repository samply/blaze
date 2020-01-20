(ns blaze.datomic.util-test
  (:require
    [blaze.datomic.test-util :as test-util]
    [blaze.datomic.util
     :refer
     [initial-version? instance-version system-version type-total type-version]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [datomic-spec.test :as dst]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (f)
  (st/unstrument))


(use-fixtures :each fixture)


(deftest initial-version?-test
  (st/unstrument `initial-version?)

  (testing "returns nil for non-existing resources"
    (is (nil? (initial-version? nil)))
    (is (nil? (initial-version? {:db/id 160127}))))

  (testing "returns truthy for initial versions"
    (is (= -3 (initial-version? {:instance/version -3})))
    (is (= -4 (initial-version? {:instance/version -4})))))


(deftest instance-version-test
  (st/unstrument `instance-version)

  (testing "returns 0 for non-existing resources"
    (is (zero? (instance-version nil)))
    (is (zero? (instance-version {:db/id 160127}))))

  (testing "returns 1 for initial versions"
    (is (= 1 (instance-version {:instance/version -3})))
    (is (= 1 (instance-version {:instance/version -4})))))


(deftest system-version-test
  (st/unstrument `system-version)

  (testing "Non-Existing System"
    (test-util/stub-entity ::db #{:system} #{{:db/id 171851}})
    (is (zero? (system-version ::db))))

  (testing "Existing System"
    (test-util/stub-entity ::db #{:system} #{{:system/version -172025}})
    (is (= 172025 (system-version ::db)))))


(deftest type-total-test
  (st/unstrument `type-total)

  (testing "Non-Existing Type"
    (test-util/stub-entity ::db #{:type} #{{:db/id 171851}})
    (is (zero? (type-total ::db "type"))))

  (testing "Existing Type"
    (test-util/stub-entity ::db #{:type} #{{:type/total -172222}})
    (is (= 172222 (type-total ::db "type")))))


(deftest type-version-test
  (st/unstrument `type-version)

  (testing "Non-Existing Type"
    (test-util/stub-entity ::db #{:type} #{{:db/id 171851}})
    (is (zero? (type-version ::db "type"))))

  (testing "Existing Type"
    (test-util/stub-entity ::db #{:type} #{{:type/version -171714}})
    (is (= 171714 (type-version ::db "type")))))

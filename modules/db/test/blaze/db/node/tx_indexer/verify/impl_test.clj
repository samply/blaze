(ns blaze.db.node.tx-indexer.verify.impl-test
  (:require
   [blaze.db.impl.codec :as codec]
   [blaze.db.node.stats :as node-stats]
   [blaze.db.node.tx-indexer.verify.impl :as impl]
   [blaze.db.test-util :as dtu]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def patient-tid (codec/tid "Patient"))
(def observation-tid (codec/tid "Observation"))
(def condition-tid (codec/tid "Condition"))

(def stats
  "Stats of a database with one Patient and one Observation and a Condition
  that was deleted."
  {:types {patient-tid {:total 1 :num-changes 1}
           observation-tid {:total 1 :num-changes 1}
           condition-tid {:total 0 :num-changes 2}}
   :system {:total 2 :num-changes 4}})

(deftest rev-include-params-test
  (with-system [{search-param-registry ::dtu/search-param-registry}
                dtu/search-param-registry-config]

    (testing "empty database"
      (is (empty? (impl/rev-include-params search-param-registry
                                           node-stats/empty-stats "Patient"))))

    (let [params (impl/rev-include-params search-param-registry stats "Patient")]

      (testing "contains the search params of Observation pointing to Patient"
        (is (some #{["Observation" "subject"]} params))
        (is (some #{["Observation" "patient"]} params)))

      (testing "contains the search params of Patient pointing to Patient"
        (is (some #{["Patient" "link"]} params)))

      (testing "skips source types without resources"
        (is (every? #{"Patient" "Observation"} (map first params)))))))

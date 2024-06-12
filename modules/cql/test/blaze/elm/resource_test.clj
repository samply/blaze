(ns blaze.elm.resource-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.elm.compiler :as c]
   [blaze.elm.expression-spec]
   [blaze.elm.resource :as cr]
   [blaze.elm.resource-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- resource [db type id]
  (cr/mk-resource db (d/resource-handle db type id)))

(deftest resource-test
  (with-system-data [{:blaze.db/keys [node]} mem-node-config]
    [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

    (let [resource (resource (d/db node) "Patient" "0")]
      (testing "attach-cache"
        (is (= [resource] (st/with-instrument-disabled (c/attach-cache resource ::cache)))))

      (testing "toString"
        (is (= "Patient[id = 0, t = 1, last-change-t = 1]" (str resource)))))))

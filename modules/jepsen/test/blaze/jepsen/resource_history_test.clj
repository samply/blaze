(ns blaze.jepsen.resource-history-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir-client :as fhir-client]
   [blaze.jepsen.resource-history :as resource-history]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def base-uri "base-uri-100000")

(deftest warm-up-test
  (let [add-ids (atom #{})
        read-ids (atom #{})
        reset-ids (atom #{})
        add-calls (atom 0)
        read-calls (atom 0)
        reset-calls (atom 0)]
    (with-redefs
     [fhir-client/update
      (fn [_ {:keys [id]} _]
        (swap! add-ids conj id)
        (swap! add-calls inc)
        (ac/completed-future nil))
      fhir-client/history-instance
      (fn [_ _ id _]
        (swap! read-ids conj id)
        (swap! read-calls inc)
        (ac/completed-future []))
      fhir-client/delete-history
      (fn [_ _ id _]
        (swap! reset-ids conj id)
        (swap! reset-calls inc)
        (ac/completed-future nil))]
      (resource-history/warm-up! {:base-uri base-uri})
      (is (= resource-history/warm-up-num-ops @add-calls))
      (is (= resource-history/warm-up-num-ops @read-calls))
      (is (= (quot resource-history/warm-up-num-ops
                   resource-history/warm-up-reset-interval)
             @reset-calls))
      (is (= @add-ids @read-ids @reset-ids))
      (is (= 1 (count @add-ids))))))

(ns blaze.db.search-param-registry-test-perf
  (:require
    [blaze.db.search-param-registry :as sr]
    [blaze.test-util :refer [structure-definition-repo with-system]]
    [clojure.test :refer [deftest testing]]
    [criterium.core :as criterium]
    [taoensso.timbre :as log]))


(log/set-level! :info)


(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})


(deftest linked-compartments-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    ;; 1.6 µs - Mac mini M1
    (testing "Condition subject"
      (criterium/quick-bench
        (sr/linked-compartments
          search-param-registry
          {:fhir/type :fhir/Condition :id "0"
           :subject #fhir/Reference{:reference "Patient/1"}}))

      ;; 700 ns - Mac mini M1
      (testing "Observation subject"
        (criterium/quick-bench
          (sr/linked-compartments
            search-param-registry
            {:fhir/type :fhir/Observation :id "0"
             :subject #fhir/Reference{:reference "Patient/1"}}))))))

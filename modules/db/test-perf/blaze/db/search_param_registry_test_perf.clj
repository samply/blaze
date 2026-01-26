(ns blaze.db.search-param-registry-test-perf
  (:require
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [clojure.test :refer [deftest testing]]
   [criterium.core :as criterium]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(log/set-min-level! :info)

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}
   ::ts/not-available {}})

(deftest linked-compartments-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    ;; 1.6 µs - Mac mini M1
    (testing "Condition subject"
      (criterium/quick-bench
       (sr/linked-compartments
        search-param-registry
        {:fhir/type :fhir/Condition :id "0"
         :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}))

      ;; 700 ns - Mac mini M1
      (testing "Observation subject"
        (criterium/quick-bench
         (sr/linked-compartments
          search-param-registry
          {:fhir/type :fhir/Observation :id "0"
           :subject #fhir/Reference{:reference #fhir/string "Patient/1"}}))))))

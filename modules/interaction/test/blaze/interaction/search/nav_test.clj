(ns blaze.interaction.search.nav-test
  (:require
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.nav-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "Observation"}
   :path "/Observation"})


(deftest url-test
  (testing "url encoding of clauses"
    (testing "Observation code with URL"
      (is (= "/Observation?code=http%3A%2F%2Floinc.org%7C8480-6&__t=1"
             (nav/url match nil [["code" "http://loinc.org|8480-6"]] 1 nil)))))

  (testing "two clauses with the same code"
    (is (= "/Observation?combo-code-value-quantity=8480-6%24ge140&combo-code-value-quantity=8462-4%24ge90&__t=1"
           (nav/url match nil
                    [["combo-code-value-quantity" "8480-6$ge140"]
                     ["combo-code-value-quantity" "8462-4$ge90"]]
                    1 nil)))))

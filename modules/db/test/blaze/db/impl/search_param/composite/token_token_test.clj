(ns blaze.db.impl.search-param.composite.token-token-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string-spec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.composite.token-token]
   [blaze.db.impl.search-param.composite.token-token-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir.hash-spec]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(defn code-value-quantity [search-param-registry]
  (sr/get search-param-registry "code-value-concept" "Observation"))

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (code-value-quantity search-param-registry)]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil))))))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "unknown modifier"
      (given (search-param/validate-modifier
              (code-value-quantity search-param-registry) "unknown")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown modifier `unknown` on search parameter `code-value-concept`."))))

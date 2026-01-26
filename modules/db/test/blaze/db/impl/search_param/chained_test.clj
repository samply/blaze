(ns blaze.db.impl.search-param.chained-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string-spec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.chained]
   [blaze.db.impl.search-param.chained-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.fhir.hash-spec]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.terminology-service :as-alias ts]
   [blaze.terminology-service.not-available]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}
   ::ts/not-available {}})

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [[search-param] (sr/parse search-param-registry "Observation" "patient.gender")]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil nil))))))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "parse fails on unknown modifier"
      (is (not (ba/anomaly? (sr/parse search-param-registry "Observation" "subject:Patient.gender"))))
      (is (ba/anomaly? (sr/parse search-param-registry "Observation" "subject:Patient.unknown"))))

    (let [[search-param] (sr/parse search-param-registry "Observation" "subject:Patient.gender")]
      (testing "unknown chained modifier"
        (given (search-param/validate-modifier search-param "unknown")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `unknown` on search parameter `gender`."))

      (testing "modifier not implemented"
        (given (search-param/validate-modifier search-param "missing")
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported modifier `missing` on search parameter `gender`.")))

    (let [[search-param] (sr/parse search-param-registry "Observation" "patient.organization")]
      (testing "unknown chained modifier"
        (given (search-param/validate-modifier search-param "unknown")
          ::anom/category := ::anom/incorrect
          ::anom/message := "Unknown modifier `unknown` on search parameter `organization`."))

      (testing "chained modifier not implemented"
        (given (search-param/validate-modifier search-param "missing")
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported modifier `missing` on search parameter `organization`."))

      (testing "implemented chained modifier"
        (is (nil? (search-param/validate-modifier search-param "Organization")))))))

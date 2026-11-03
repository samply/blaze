(ns blaze.db.impl.search-param.chained-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.chained]
   [blaze.db.impl.search-param.chained-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.db.test-util :as dtu :refer [with-db with-system-data]]
   [blaze.fhir.hash-spec]
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

(deftest ordered-compartment-index-handles-test
  (with-system [{search-param-registry ::dtu/search-param-registry} dtu/search-param-registry-config]
    (let [[search-param] (sr/parse search-param-registry "Observation" "patient.gender")]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil nil))))))

(def ^:private unsupported-estimation-search-param
  (reify p/SearchParam
    (-estimated-scan-size [_ _ _ _ _]
      (ba/unsupported))))

(deftest estimated-scan-size-test
  (testing "returns all iterators if the estimation stops early"
    (with-system-data [{:blaze.db/keys [node] search-param-registry ::dtu/search-param-registry} dtu/config]
      [(mapv
        (fn [i] [:put {:fhir/type :fhir/Patient :id (str i) :gender #fhir/code "male"}])
        (range 100))]

      (let [[search-param] (sr/parse search-param-registry "Observation" "subject:Patient.gender")
            search-param (assoc search-param :ref-search-param unsupported-estimation-search-param)
            compiled-value @(p/-compile-value search-param nil "male")]
        (with-db [db node]
          (is (ba/unsupported? (p/-estimated-scan-size search-param db (codec/tid "Observation") nil compiled-value)))

          (is (zero? (dtu/num-borrowed-iterators db))))))))

(deftest validate-modifier-test
  (with-system [{search-param-registry ::dtu/search-param-registry} dtu/search-param-registry-config]
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

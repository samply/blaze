(ns blaze.db.impl.search-param.has-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string-spec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.has]
   [blaze.db.impl.search-param.has-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
   [blaze.db.test-util :as dtu]
   [blaze.fhir.hash-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest estimated-scan-size-test
  (with-system [{search-param-registry ::dtu/search-param-registry} dtu/search-param-registry-config]
    (let [search-param (sr/get search-param-registry "_has" "Resource")]
      (is (ba/unsupported? (p/-estimated-scan-size search-param nil nil nil nil))))))

(deftest ordered-index-handles-test
  (with-system [{search-param-registry ::dtu/search-param-registry} dtu/search-param-registry-config]
    (let [search-param (sr/get search-param-registry "_has" "Resource")]
      (is (false? (p/-supports-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-index-handles search-param nil nil nil nil nil))))))

(deftest ordered-compartment-index-handles-test
  (with-system [{search-param-registry ::dtu/search-param-registry} dtu/search-param-registry-config]
    (let [search-param (sr/get search-param-registry "_has" "Resource")]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil nil))))))

(deftest validate-modifier-test
  (with-system [{search-param-registry ::dtu/search-param-registry} dtu/search-param-registry-config]
    (testing "unknown modifier is ignored"
      (is (nil? (search-param/validate-modifier
                 (sr/get search-param-registry "_has" "Resource") "unknown"))))))

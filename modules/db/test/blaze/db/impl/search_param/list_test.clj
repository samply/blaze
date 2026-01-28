(ns blaze.db.impl.search-param.list-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.byte-string-spec]
   [blaze.db.impl.protocols :as p]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.db.search-param-registry-spec]
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

(defn list-param [search-param-registry]
  (sr/get search-param-registry "_list" "Patient"))

(def ^:private config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo
    :terminology-service (ig/ref ::ts/not-available)}
   ::ts/not-available {}})

(deftest list-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (list-param search-param-registry)
      :name := "_list"
      :code := "_list")))

(deftest estimated-scan-size-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (list-param search-param-registry)]
      (is (ba/unsupported? (p/-estimated-scan-size search-param nil nil nil nil))))))

(deftest ordered-compartment-index-handles-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (let [search-param (list-param search-param-registry)]
      (is (false? (p/-supports-ordered-compartment-index-handles search-param nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil)))
      (is (ba/unsupported? (p/-ordered-compartment-index-handles search-param nil nil nil nil nil nil))))))

(deftest validate-modifier-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (testing "unknown modifier"
      (given (search-param/validate-modifier
              (list-param search-param-registry) "unknown")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Unknown modifier `unknown` on search parameter `_list`."))))

(ns blaze.db.impl.search-param.list-test
  (:require
   [blaze.byte-string-spec]
   [blaze.db.impl.search-param-spec]
   [blaze.db.search-param-registry :as sr]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(defn list-param [search-param-registry]
  (sr/get search-param-registry "_list" "Patient"))

(def config
  {:blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}})

(deftest list-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} config]
    (given (list-param search-param-registry)
      :name := "_list"
      :code := "_list")))

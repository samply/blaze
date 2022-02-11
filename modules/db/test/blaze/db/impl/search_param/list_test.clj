(ns blaze.db.impl.search-param.list-test
  (:require
    [blaze.byte-string-spec]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.structure-definition-repo]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn list-param [search-param-registry]
  (sr/get search-param-registry "_list" "Patient"))


(def system
  {:blaze.fhir/structure-definition-repo {}
   :blaze.db/search-param-registry
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}})


(deftest list-param-test
  (with-system [{:blaze.db/keys [search-param-registry]} system]
    (given (list-param search-param-registry)
      :name := "_list"
      :code := "_list")))

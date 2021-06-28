(ns blaze.db.resource-handle-cache-test
  (:require
    [blaze.db.resource-handle-cache]
    [blaze.db.test-util :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Cache]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- cache [max-size]
  (-> (ig/init
        {:blaze.db/resource-handle-cache
         {:max-size max-size}})
      (:blaze.db/resource-handle-cache)))


(deftest init-test
  (testing "invalid max-size"
    (given-thrown (cache nil)
      :key := :blaze.db/resource-handle-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?))

  (testing "success"
    (is (instance? Cache (cache 0)))))

(ns blaze.db.resource-handle-cache-test
  (:require
    [blaze.db.resource-handle-cache]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Cache]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "invalid max-size"
    (given-thrown (ig/init {:blaze.db/resource-handle-cache nil})
      :key := :blaze.db/resource-handle-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid max-size"
    (given-thrown (ig/init {:blaze.db/resource-handle-cache {:max-size ::invalid}})
      :key := :blaze.db/resource-handle-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?
      [:explain ::s/problems 0 :val] := ::invalid))

  (testing "success"
    (with-system [{:blaze.db/keys [resource-handle-cache]}
                  {:blaze.db/resource-handle-cache {:max-size 100}}]
      (is (instance? Cache resource-handle-cache))))

  (testing "produces no cache for :max-size of 0"
    (with-system [{:blaze.db/keys [resource-handle-cache]}
                  {:blaze.db/resource-handle-cache {:max-size 0}}]
      (is (nil? resource-handle-cache)))))

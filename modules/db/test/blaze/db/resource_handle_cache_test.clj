(ns blaze.db.resource-handle-cache-test
  (:require
    [blaze.db.resource-handle-cache]
    [blaze.test-util :refer [given-thrown with-system]]
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


(def system
  {:blaze.db/resource-handle-cache
   {:max-size 100}})


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
    (with-system [{:blaze.db/keys [resource-handle-cache]} system]
      (is (instance? Cache resource-handle-cache)))))

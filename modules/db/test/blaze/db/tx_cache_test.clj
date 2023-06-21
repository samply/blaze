(ns blaze.db.tx-cache-test
  (:require
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.tx-cache]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache LoadingCache]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(def config
  {:blaze.db/tx-cache
   {:kv-store (ig/ref ::kv/mem)}
   ::kv/mem {:column-families {}}})


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.db/tx-cache nil})
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing store"
    (given-thrown (ig/init {:blaze.db/tx-cache {}})
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :kv-store))))

  (testing "invalid store"
    (given-thrown (ig/init {:blaze.db/tx-cache {:kv-store nil}})
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `blaze.db.kv/store?))

  (testing "invalid max-size"
    (given-thrown (ig/init {:blaze.db/tx-cache
                            {:kv-store (ig/ref ::kv/mem)
                             :max-size nil}
                            ::kv/mem {:column-families {}}})
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?)))


(deftest empty-store-test
  (with-system [{cache :blaze.db/tx-cache} config]
    (is (nil? (.get ^LoadingCache cache 0)))))

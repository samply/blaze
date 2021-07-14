(ns blaze.db.tx-cache-test
  (:require
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [blaze.db.test-util :refer [given-thrown]]
    [blaze.db.tx-cache]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache LoadingCache]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- cache [kv-store max-size]
  (-> (ig/init
        {:blaze.db/tx-cache
         {:kv-store kv-store
          :max-size max-size}})
      :blaze.db/tx-cache))


(deftest failing-init-test
  (testing "missing store"
    (given-thrown (ig/init {:blaze.db/tx-cache {}})
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :kv-store))))

  (testing "invalid store"
    (given-thrown (ig/init {:blaze.db/tx-cache {:kv-store nil}})
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn [~'%] (satisfies? kv/KvStore ~'%))))

  (testing "invalid max-size"
    (given-thrown (ig/init {:blaze.db/tx-cache {:kv-store (new-mem-kv-store) :max-size nil}})
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `nat-int?)))


(deftest empty-store-test
  (let [^LoadingCache cache (cache (new-mem-kv-store) 0)]
    (is (nil? (.get cache 0)))))

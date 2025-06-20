(ns blaze.db.tx-cache-test
  (:require
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.tx-cache :as tx-cache]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache LoadingCache]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def config
  {:blaze.db/tx-cache
   {:kv-store (ig/ref ::kv/mem)}
   ::kv/mem {:column-families {}}})

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.db/tx-cache nil}
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing store"
    (given-failed-system {:blaze.db/tx-cache {}}
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn [~'%] (contains? ~'% :kv-store))))

  (testing "invalid store"
    (given-failed-system (assoc-in config [:blaze.db/tx-cache :kv-store] ::invalid)
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/kv-store]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid max-size"
    (given-failed-system (assoc-in config [:blaze.db/tx-cache :max-size] ::invalid)
      :key := :blaze.db/tx-cache
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::tx-cache/max-size]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(deftest empty-store-test
  (with-system [{cache :blaze.db/tx-cache} config]
    (is (nil? (.get ^LoadingCache cache 0)))))

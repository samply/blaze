(ns blaze.page-id-cipher-test
  (:require
   [blaze.db.kv :as kv]
   [blaze.db.kv.mem]
   [blaze.db.node :as node :refer [node?]]
   [blaze.db.resource-store :as rs]
   [blaze.db.resource-store.kv :as rs-kv]
   [blaze.db.search-param-registry]
   [blaze.db.tx-cache]
   [blaze.db.tx-log :as tx-log]
   [blaze.db.tx-log.local]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.log]
   [blaze.module.test-util :refer [with-system]]
   [blaze.page-id-cipher]
   [blaze.page-id-cipher.spec]
   [blaze.scheduler.spec :refer [scheduler?]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.datafy :as datafy]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(derive :blaze.db.admin/node :blaze.db/node)

(def config
  {:blaze/page-id-cipher
   {:node (ig/ref :blaze.db.admin/node)
    :scheduler (ig/ref :blaze/scheduler)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :key-rotation-period (time/seconds 1)}

   :blaze.db.admin/node
   {:tx-log (ig/ref :blaze.db.admin/tx-log)
    :tx-cache (ig/ref :blaze.db.admin/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node.admin/indexer-executor)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-indexer (ig/ref :blaze.db.node.admin/resource-indexer)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :scheduler (ig/ref :blaze/scheduler)
    :poll-timeout (time/millis 10)}

   [::tx-log/local :blaze.db.admin/tx-log]
   {:kv-store (ig/ref :blaze.db.admin/transaction-kv-store)
    :clock (ig/ref :blaze.test/fixed-clock)}

   [::kv/mem :blaze.db.admin/transaction-kv-store]
   {:column-families {}}

   [:blaze.db/tx-cache :blaze.db.admin/tx-cache]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)}

   [::node/indexer-executor :blaze.db.node.admin/indexer-executor]
   {}

   [::kv/mem :blaze.db.admin/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}}

   [::node/resource-indexer :blaze.db.node.admin/resource-indexer]
   {:kv-store (ig/ref :blaze.db.admin/index-kv-store)
    :resource-store (ig/ref :blaze.db/resource-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :executor (ig/ref :blaze.db.node.resource-indexer.admin/executor)}

   [:blaze.db.node.resource-indexer/executor :blaze.db.node.resource-indexer.admin/executor]
   {}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :executor (ig/ref ::rs-kv/executor)}

   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}

   ::rs-kv/executor {}

   :blaze.db/search-param-registry
   {:structure-definition-repo structure-definition-repo}

   :blaze/scheduler {}

   :blaze.test/fixed-clock {}

   :blaze.test/fixed-rng-fn {}})

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/page-id-cipher nil})
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze/page-id-cipher {}})
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-thrown (ig/init {:blaze/page-id-cipher {:node ::invalid}})
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `node?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid scheduler"
    (given-thrown (ig/init {:blaze/page-id-cipher {:scheduler ::invalid}})
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `scheduler?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze/page-id-cipher {:clock ::invalid}})
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `time/clock?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-thrown (ig/init {:blaze/page-id-cipher {:rng-fn ::invalid}})
      :key := :blaze/page-id-cipher
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :scheduler))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 3 :pred] := `fn?
      [:cause-data ::s/problems 3 :val] := ::invalid))

  (testing "success"
    (with-system [{:blaze/keys [page-id-cipher]} config]
      (is (s/valid? :blaze/page-id-cipher page-id-cipher)))))

(deftest key-rotation-test
  (with-system [{:blaze/keys [page-id-cipher]} config]
    (Thread/sleep 1500)
    (given (datafy/datafy (:key-set-handle @(:state page-id-cipher)))
      count := 2
      [0 :primary] := true
      [0 :status] := :key.status/enabled
      [1 :primary] := false
      [1 :status] := :key.status/enabled)))

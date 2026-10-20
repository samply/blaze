(ns blaze.db.impl.index.patient-last-change-test
  (:require
   [blaze.db.impl.index.patient-last-change :as plc]
   [blaze.db.impl.index.patient-last-change-spec]
   [blaze.db.kv :as kv]
   [blaze.db.kv.iter-pool :as ip]
   [blaze.db.kv.mem]
   [blaze.db.kv.mem-spec]
   [blaze.db.kv.protocols :as kv-p]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(def config
  {::kv/mem {:column-families {:patient-last-change-index nil}}})

(defn- iterator-counting-snapshot
  "Returns a pooling snapshot of `kv-store` that increments `num-iterators` for
  each iterator newly created because the pool had none available."
  ^AutoCloseable
  [kv-store num-iterators]
  (let [snapshot (kv-p/-new-snapshot kv-store)]
    (ip/pooling-snapshot
     (reify
       kv-p/KvSnapshot
       (-new-iterator [_ column-family]
         (swap! num-iterators inc)
         (kv-p/-new-iterator snapshot column-family))
       (-snapshot-get [_ column-family key]
         (kv-p/-snapshot-get snapshot column-family key))
       AutoCloseable
       (close [_]
         (.close ^AutoCloseable snapshot))))))

(defn- last-change-ts [snapshot t handles]
  (into
   []
   (plc/last-change-t-xf snapshot t (fn [handle t] [(:id handle) t]))
   handles))

(def ^:private patient-0 {:fhir/type :fhir/Patient :id "0"})
(def ^:private patient-1 {:fhir/type :fhir/Patient :id "1"})
(def ^:private patient-2 {:fhir/type :fhir/Patient :id "2"})

(deftest last-change-t-xf-test
  (with-system [{kv-store ::kv/mem} config]
    (kv/put! kv-store [(plc/index-entry "0" 1) (plc/index-entry "1" 2)])

    (testing "emits the last change t of patients and nil for patients without resources"
      (with-open [snapshot (kv/new-snapshot kv-store)]
        (is (= [["0" 1] ["1" 2] ["2" nil]]
               (last-change-ts snapshot 2 [patient-0 patient-1 patient-2])))

        (testing "respects t"
          (is (= [["0" 1] ["1" nil]]
                 (last-change-ts snapshot 1 [patient-0 patient-1]))))))

    (testing "opens only one iterator for all patients"
      (let [num-iterators (atom 0)]
        (with-open [snapshot (iterator-counting-snapshot kv-store num-iterators)]
          (last-change-ts snapshot 2 [patient-0 patient-1 patient-2])
          (is (= 1 @num-iterators))

          (testing "the iterator is closed on completion and so reused from the pool"
            (last-change-ts snapshot 2 [patient-0 patient-1 patient-2])
            (is (= 1 @num-iterators))))))))

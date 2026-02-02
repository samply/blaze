(ns blaze.db.impl.index.plan-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.db.impl.index.plan :as plan]
   [blaze.db.impl.search-param :as search-param]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest group-by-estimated-scan-size-test
  (testing "a single clause will be small by default"
    (st/with-instrument-disabled
      (doseq [size [0 (ba/unsupported)]]
        (with-redefs [search-param/estimated-scan-size
                      (fn [search-param batch-db tid modifier compiled-values]
                        (assert (= ::search-param search-param))
                        (assert (= ::batch-db batch-db))
                        (assert (= ::tid tid))
                        (assert (= ::modifier modifier))
                        (assert (= ::compiled-values compiled-values))
                        size)]
          (given (plan/group-by-estimated-scan-size
                  ::batch-db ::tid [[[::search-param ::modifier [] ::compiled-values]]])
            :small := [[[::search-param ::modifier [] ::compiled-values]]]
            :large :? empty?)))))

  (testing "a clause 10x larger than the smallest will be large"
    (st/with-instrument-disabled
      (with-redefs [search-param/estimated-scan-size
                    (fn [search-param _ _ _ _]
                      (if (= ::search-param-small search-param) 1 10))]
        (given (plan/group-by-estimated-scan-size
                ::batch-db ::tid [[[::search-param-small]]
                                  [[::search-param-large]]])
          :small := [[[::search-param-small]]]
          :large := [[[::search-param-large]]]))))

  (testing "a clause only 9x larger than the smallest will be still small"
    (st/with-instrument-disabled
      (with-redefs [search-param/estimated-scan-size
                    (fn [search-param _ _ _ _]
                      (if (= ::search-param-small search-param) 1 9))]
        (given (plan/group-by-estimated-scan-size
                ::batch-db ::tid [[[::search-param-small]]
                                  [[::search-param-large]]])
          :small := [[[::search-param-small]] [[::search-param-large]]]
          :large :? empty?))))

  (testing "a secand clause without size will be large"
    (st/with-instrument-disabled
      (with-redefs [search-param/estimated-scan-size
                    (fn [search-param _ _ _ _]
                      (if (= ::search-param-small search-param) 1 (ba/unsupported)))]
        (given (plan/group-by-estimated-scan-size
                ::batch-db ::tid [[[::search-param-small]]
                                  [[::search-param-no-size]]])
          :small := [[[::search-param-small]]]
          :large := [[[::search-param-no-size]]])))))

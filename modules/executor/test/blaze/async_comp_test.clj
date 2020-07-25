(ns blaze.async-comp-test
  (:require
    [blaze.async-comp :as ac]
    [blaze.async-comp-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest deref-test
  (testing "on completed future"
    (is (= ::x @(ac/completed-future ::x))))

  (testing "on failed future"
    (try
      @(ac/failed-future (ex-info "e" {}))
      (catch Exception e
        (is (= "e" (ex-message (ex-cause e))))))))

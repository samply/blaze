(ns blaze.validator.extern.semaphore-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.test-util :as tu]
   [blaze.validator.extern.semaphore :as sem]
   [blaze.validator.extern.semaphore-spec]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest semaphore-test
  (testing "permits are handed out immediately while available"
    (let [s (sem/semaphore 2)]
      (is (ac/done? (sem/acquire! s)))
      (is (ac/done? (sem/acquire! s)))))

  (testing "an acquire beyond the limit stays pending until a release"
    (let [s (sem/semaphore 1)]
      (is (ac/done? (sem/acquire! s)))
      (let [f (sem/acquire! s)]
        (is (not (ac/done? f)))
        (sem/release! s)
        (is (ac/done? f)))))

  (testing "waiters are served in FIFO order"
    (let [s (sem/semaphore 1)]
      (sem/acquire! s)
      (let [f2 (sem/acquire! s)
            f3 (sem/acquire! s)]
        (is (not (ac/done? f2)))
        (is (not (ac/done? f3)))
        (sem/release! s)
        (is (ac/done? f2))
        (is (not (ac/done? f3)))
        (sem/release! s)
        (is (ac/done? f3)))))

  (testing "a released permit is available for a later acquire"
    (let [s (sem/semaphore 1)]
      (sem/acquire! s)
      (sem/release! s)
      (is (ac/done? (sem/acquire! s))))))

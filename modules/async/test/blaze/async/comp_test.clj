(ns blaze.async.comp-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.executors :as ex]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.util.concurrent TimeUnit]))


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
      @(ac/failed-future (ex-info "e" {:a :b}))
      (catch Exception e
        (is (= "e" (ex-message (ex-cause e))))
        (is (= :b (:a (ex-data (ex-cause e)))))))))


(deftest all-of-test
  (testing "with two successful futures"
    (let [a (ac/future)
          b (ac/future)
          all (ac/all-of [a b])]
      (ac/complete! a 1)
      (ac/complete! b 2)

      (is (nil? @all))))

  (testing "with one failed future"
    (let [a (ac/future)
          b (ac/future)
          all (ac/all-of [a b])]
      (ac/complete! a 1)
      (ac/complete-exceptionally! b (ex-info "e" {}))

      (try
        @all
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest complete-on-timeout!-test
  (testing "with timeout happen"
    (let [f (ac/future)]
      (ac/complete-on-timeout! f :a 1 TimeUnit/MILLISECONDS)
      (Thread/sleep 10)
      (ac/complete! f :b)
      (is (= :a @f))))

  (testing "without timeout happen"
    (let [f (ac/future)]
      (ac/complete-on-timeout! f :a 10 TimeUnit/MILLISECONDS)
      (ac/complete! f :b)
      (is (= :b @f)))))


(deftest join-test
  (testing "on completed future"
    (is (= ::x (ac/join (ac/completed-future ::x)))))

  (testing "on failed future"
    (try
      (ac/join (ac/failed-future (ex-info "e" {:a :b})))
      (catch Exception e
        (is (= "e" (ex-message (ex-cause e))))
        (is (= :b (:a (ex-data (ex-cause e)))))))))


(deftest supply-test
  (testing "successful"
    (is (= 1 @(ac/supply 1))))

  (testing "error"
    (let [f (ac/supply (throw (ex-info "e" {})))]
      (try
        @f
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest supply-async-test
  (testing "successful"
    (is (= 1 @(ac/supply-async (constantly 1))))

    (testing "with executor"
      (is (= 1 @(ac/supply-async (constantly 1) (ex/single-thread-executor))))))

  (testing "error"
    (let [f (ac/supply-async (fn [] (throw (ex-info "e" {})))
                             (ex/single-thread-executor))]
      (try
        @f
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))

    (testing "with executor"
      (let [f (ac/supply-async (fn [] (throw (ex-info "e" {})))
                               (ex/single-thread-executor))]
        (try
          @f
          (catch Exception e
            (is (= "e" (ex-message (ex-cause e))))))))))


(deftest then-apply-test
  (let [f (ac/future)
        f' (ac/then-apply f inc)]
    (ac/complete! f 1)
    (is (= 2 @f'))))


(deftest then-apply-async-test
  (testing "with default executor"
    (let [f (ac/future)
          f' (ac/then-apply-async f inc)]
      (ac/complete! f 1)
      (is (= 2 @f'))))

  (testing "with custom executor"
    (let [f (ac/future)
          f' (ac/then-apply-async f inc (ex/single-thread-executor))]
      (ac/complete! f 1)
      (is (= 2 @f')))))


(deftest then-compose-test
  (let [f (ac/future)
        f' (ac/then-compose f ac/completed-future)]
    (ac/complete! f 1)
    (is (= 1 @f'))))


(deftest handle-test
  (testing "with success"
    (let [f (ac/future)
          f' (ac/handle f (fn [x _] x))]
      (ac/complete! f 1)
      (is (= 1 @f'))))

  (testing "with error"
    (let [f (ac/future)
          f' (ac/handle f (fn [_ e] (ex-message e)))]
      (ac/complete-exceptionally! f (ex-info "e" {}))
      (is (= "e" @f')))))


(deftest exceptionally-test
  (let [f (ac/future)
        f' (ac/exceptionally f (fn [e] (ex-message e)))]
    (ac/complete-exceptionally! f (ex-info "e" {}))
    (is (= "e" @f'))))


(deftest when-complete-test
  (testing "with success"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete f (fn [x _] (ac/complete! f' (inc x))))]
      (ac/complete! f 1)
      (is (= 2 @f'))
      (is (= 1 @f''))))

  (testing "with error"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete f (fn [_ e] (ac/complete! f' (ex-message e))))]
      (ac/complete-exceptionally! f (ex-info "e" {}))
      (is (= "e" @f'))
      (try
        @f''
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest when-complete-async-test
  (testing "with success"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete-async f (fn [x _] (ac/complete! f' (inc x)))
                                      (ex/single-thread-executor))]
      (ac/complete! f 1)
      (is (= 2 @f'))
      (is (= 1 @f''))))

  (testing "with error"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete-async f (fn [_ e] (ac/complete! f' (ex-message e)))
                                      (ex/single-thread-executor))]
      (ac/complete-exceptionally! f (ex-info "e" {}))
      (is (= "e" @f'))
      (try
        @f''
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest ->completable-future-test
  (is (ac/completable-future? (ac/->completable-future (ac/future)))))

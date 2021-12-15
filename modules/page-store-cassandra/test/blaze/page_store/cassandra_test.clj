(ns blaze.page-store.cassandra-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.cassandra :as cass]
    [blaze.cassandra-spec]
    [blaze.page-store :as page-store]
    [blaze.page-store.cassandra]
    [blaze.page-store.cassandra.codec :as codec]
    [blaze.page-store.cassandra.codec-spec]
    [blaze.page-store.cassandra.statement :as statement]
    [blaze.test-util :refer [given-thrown with-system]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cuerdas.core :as str]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [java.security SecureRandom]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {::page-store/cassandra nil})
      :key := ::page-store/cassandra
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid contact-points"
    (given-thrown (ig/init {::page-store/cassandra {:secure-rng (SecureRandom.)
                                                    :contact-points ::invalid}})
      :key := ::page-store/cassandra
      :reason := ::ig/build-failed-spec
      [:value :contact-points] := ::invalid
      [:explain ::s/problems 0 :path] := [:contact-points]
      [:explain ::s/problems 0 :val] := ::invalid)))


(defn- prepare [session statement]
  (assert (= ::session session))
  (condp = statement
    statement/get-statement
    ::prepared-get-statement
    (statement/put-statement "TWO")
    ::prepared-put-statement))


(def system
  {::page-store/cassandra {:secure-rng (ig/ref :blaze.test/fixed-rng)}
   :blaze.test/fixed-rng {}})


(def clauses [["active" "true"]])
(def token (str (str/repeat "A" 31) "B"))


(deftest put-test
  (testing "success"
    (with-redefs
      [cass/session (fn [_] ::session)
       cass/prepare prepare
       cass/bind (fn [prepared-statement & params]
                   (assert (= ::prepared-put-statement prepared-statement))
                   (assert (= [token (codec/encode clauses)] params))
                   ::bound-put-statement)
       cass/execute (fn [session statement]
                      (assert (= ::session session))
                      (assert (= ::bound-put-statement statement))
                      (ac/completed-future ::result-set))
       cass/close (fn [session]
                    (assert (= ::session session)))]
      (with-system [{store ::page-store/cassandra} system]
        (is (= token @(page-store/put! store clauses)))))))

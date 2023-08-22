(ns blaze.admin-api-test
  (:require
    [blaze.admin-api]
    [blaze.db.kv.rocksdb.protocols :as p]
    [blaze.module.test-util :refer [with-system]]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/admin-api nil})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze/admin-api {}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :index-kv-store))))

  (testing "invalid index-kv-store"
    (given-thrown (ig/init {:blaze/admin-api {:index-kv-store ::invalid}})
      :key := :blaze/admin-api
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :context-path)))))


(defmethod ig/init-key ::rocksdb
  [_ {:keys [column-families]}]
  (reify p/Rocks
    (-column-families [_]
      (keys column-families))

    (-table-properties [_ column-family]
      (when (column-families column-family)
        [{:name (str (name column-family) "/table-1")
          :data-size 193338}]))))


(def config
  {:blaze/admin-api
   {:context-path "/fhir"
    :index-kv-store (ig/ref :blaze.db/index-kv-store)}

   [::rocksdb :blaze.db/index-kv-store]
   {:column-families
    {:column-family-1 {}
     :column-family-2 {}}}})


(defmacro with-handler [[handler-binding] & body]
  `(with-system [{handler# :blaze/admin-api} config]
     (let [~handler-binding handler#]
       ~@body)))


(deftest rocksdb-column-families-test
  (with-handler [handler]
    (testing "success"
      (given @(handler
                {:request-method :get
                 :uri "/fhir/__admin/rocksdb/index/column-families"})
        :status := 200
        [:body :column-families] := ["column-family-1" "column-family-2"]))))


(deftest rocksdb-tables-test
  (with-handler [handler]
    (testing "not found"
      (given @(handler
                {:request-method :get
                 :uri "/fhir/__admin/foo"})
        :status := 404))

    (testing "success"
      (given @(handler
                {:request-method :get
                 :uri "/fhir/__admin/rocksdb/index/column-families/column-family-1/tables"})
        :status := 200
        [:body :tables 0 :name] := "column-family-1/table-1"
        [:body :tables 0 :data-size] := 193338))))

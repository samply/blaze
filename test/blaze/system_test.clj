(ns blaze.system-test
  (:require
    [blaze.system :as system]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is]]
    [datomic-spec.test :as dst]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest resolve-config-test
  (are [config env res] (= res (system/resolve-config config env))
    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    {"SERVER_PORT" "80"}
    {:a 80}

    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    nil
    {:a 8080}

    {:a (system/->Cfg "SERVER_PORT" (s/spec nat-int?) 8080)}
    {"SERVER_PORT" "a"}
    {:a ::s/invalid}))

(deftest init-shutdown-test
  (is (nil? (system/shutdown! (system/init! {})))))

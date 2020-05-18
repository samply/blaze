(ns blaze.system-test
  (:require
    [blaze.system :as system]
    [blaze.system-spec]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
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
    {:a ::s/invalid})

  (testing "Blank env vars are handled same as missing ones"
    (are [config env res] (= res (system/resolve-config config env))
      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) nil)}
      {"PROXY_HOST" ""}
      {:a nil}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) nil)}
      {}
      {:a nil}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) "default")}
      {"PROXY_HOST" ""}
      {:a "default"}

      {:a (system/->Cfg "PROXY_HOST" (s/spec string?) "default")}
      {}
      {:a "default"})))

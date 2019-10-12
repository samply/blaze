(ns blaze.system-test
  (:require
    [blaze.system :as system]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic-spec.test :as dst]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(use-fixtures :each fixture)


(deftest init-shutdown-test
  (is (nil? (system/shutdown! (system/init! {})))))

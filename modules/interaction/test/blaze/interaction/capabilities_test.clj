(ns blaze.interaction.capabilities-test
  (:require
    [blaze.interaction.capabilities :refer [handler]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest handler-test
  (is (map? @((handler "foo" "bar" []) {}))))

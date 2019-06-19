(ns blaze.system-test
  (:require
    [blaze.system :as system]
    [clojure.test :refer :all]))


(deftest init-shutdown-test
  (system/shutdown! (system/init! {})))

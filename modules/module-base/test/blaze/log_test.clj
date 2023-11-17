(ns blaze.log-test
  (:require
   [blaze.log]
   [clojure.test :refer [deftest is testing]]
   [taoensso.timbre :as log]))

(deftest log-test
  (testing "logging works"
    (is (nil? (log/info "test")))))

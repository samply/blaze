(ns blaze.page-store.local.token-test
  (:require
    [blaze.page-store.local.token :as token]
    [clojure.test :refer [deftest is]])
  (:import
    [java.security SecureRandom]))


(deftest generate-test
  ;; Base 32 = 5 bit per char = 32 * 5 bit = 160 bit
  (is (= 32 (count (token/generate (SecureRandom.))))))

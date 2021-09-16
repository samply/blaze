(ns blaze.db.impl.arrays-support-test
  (:require
    [blaze.db.impl.arrays-support :as arrays-support]
    [clojure.test :refer [deftest are is testing]]))


(deftest new-length-test
  (are [o m p n] (= n (arrays-support/new-length o m p))
    1 1 1 2
    1 2 1 3
    (- Integer/MAX_VALUE 9) 1 0 (- Integer/MAX_VALUE 8)
    (- Integer/MAX_VALUE 9) 0 1 (- Integer/MAX_VALUE 8)
    (- Integer/MAX_VALUE 8) 0 1 (- Integer/MAX_VALUE 8)
    (- Integer/MAX_VALUE 8) 8 0 Integer/MAX_VALUE)

  (testing "throws an OutOfMemoryError because the minimum required length exceeds Integer/MAX_VALUE"
    (is (thrown? OutOfMemoryError (arrays-support/new-length (- Integer/MAX_VALUE 8) 9 0)))))

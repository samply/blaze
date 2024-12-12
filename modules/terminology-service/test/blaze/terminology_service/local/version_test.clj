(ns blaze.terminology-service.local.version-test
  (:require
   [blaze.terminology-service.local.version :as version]
   [clojure.test :refer [deftest is]]))

(deftest cmp-test
  (is (zero? (version/cmp nil nil)))
  (is (= -1 (version/cmp nil "")))
  (is (= 1 (version/cmp "" nil)))
  (is (zero? (version/cmp "" "")))
  (is (= -1 (version/cmp "1" "2")))
  (is (zero? (version/cmp "1" "1")))
  (is (= 1 (version/cmp "2" "1")))
  (is (= -1 (version/cmp "a" "b")))
  (is (zero? (version/cmp "a" "a")))
  (is (= 1 (version/cmp "b" "a")))
  (is (= -1 (version/cmp "1" "a")))
  (is (= 1 (version/cmp "a" "1")))
  (is (= -1 (version/cmp "1.2" "1.10")))
  (is (zero? (version/cmp "1.2" "1.2")))
  (is (= 1 (version/cmp "1.10" "1.2")))
  (is (= -1 (version/cmp "1" "1.1")))
  (is (= 1 (version/cmp "1.1" "1"))))

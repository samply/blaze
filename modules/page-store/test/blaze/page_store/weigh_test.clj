(ns blaze.page-store.weigh-test
  (:require
    [blaze.page-store.weigh :as w]
    [clojure.test :refer [are deftest testing]]
    [cuerdas.core :as c-str]))


(deftest weigh-test
  (testing "String"
    (are [s size] (= size (w/weigh s))
      "" 40
      "a" 48
      (c-str/repeat "a" 8) 48
      (c-str/repeat "a" 9) 56))

  (testing "Vector"
    (are [v size] (= size (w/weigh v))
      [] 240
      [:x] 248
      [""] 288
      (vec (repeat 2 :x)) 248
      (vec (repeat 3 :x)) 256
      (vec (repeat 3 "")) 376
      (vec (repeat 4 :x)) 256
      (vec (repeat 5 :x)) 264)))

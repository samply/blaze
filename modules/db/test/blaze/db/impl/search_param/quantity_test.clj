(ns blaze.db.impl.search-param.quantity-test
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest]]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def value-quantity
  (sr/get search-param-registry "value-quantity" "Observation"))


(defn compile-quantity-value [value]
  (let [[[op lower-bound exact-value upper-bound]]
        (search-param/compile-values value-quantity [value])]
    [op (codec/hex lower-bound) (codec/hex exact-value) (codec/hex upper-bound)]))


(defn hex-quantity [unit value]
  (codec/hex (codec/quantity unit value)))


(deftest compile-values-test
  (are [value op lower-bound exact-value upper-bound]
    (= [op lower-bound exact-value upper-bound] (compile-quantity-value value))

    "23.4" :eq (hex-quantity nil 23.35M) (hex-quantity nil 23.40M) (hex-quantity nil 23.45M)
    "23.0|kg/m2" :eq (hex-quantity "kg/m2" 22.95M) (hex-quantity "kg/m2" 23.00M) (hex-quantity "kg/m2" 23.05M)
    "ge23" :ge (hex-quantity nil 22.5M) (hex-quantity nil 23.00M) (hex-quantity nil 23.5M)
    "0.1" :eq (hex-quantity nil 0.05M) (hex-quantity nil 0.10M) (hex-quantity nil 0.15M)
    "0" :eq (hex-quantity nil -0.5M) (hex-quantity nil 0.00M) (hex-quantity nil 0.5M)
    "0.0" :eq (hex-quantity nil -0.05M) (hex-quantity nil 0.00M) (hex-quantity nil 0.05M)))

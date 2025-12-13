(ns blaze.coerce-env-test
  (:require
   [blaze.coerce-env :as ce]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest bindings-and-settings-test
  (let [conf ["BASE_URL" ce/coerce-base-url "http://localhost:8080"]]
    (given (ce/bindings-and-settings {} 'base-url conf)
      :bind-map := {'base-url "http://localhost:8080"}
      [:settings "BASE_URL" :value] := "http://localhost:8080"
      [:settings "BASE_URL" :default-value] := "http://localhost:8080")

    (given (ce/bindings-and-settings {"BASE_URL" "foo"} 'base-url conf)
      :bind-map := {'base-url "foo"}
      [:settings "BASE_URL" :value] := "foo"
      [:settings "BASE_URL" :default-value] := "http://localhost:8080"))

  (let [conf ["DB_SCALING_FACTOR" ce/coerce-db-scaling-factor "1"]]
    (given (ce/bindings-and-settings {} 'db-scaling-factor conf)
      :bind-map := {'db-scaling-factor 1}
      [:settings "DB_SCALING_FACTOR" :value] := 1
      [:settings "DB_SCALING_FACTOR" :default-value] := "1")

    (given (ce/bindings-and-settings {"DB_SCALING_FACTOR" "4"} 'db-scaling-factor conf)
      :bind-map := {'db-scaling-factor 4}
      [:settings "DB_SCALING_FACTOR" :value] := 4
      [:settings "DB_SCALING_FACTOR" :default-value] := "1")

    (given (ce/bindings-and-settings {"DB_SCALING_FACTOR" "3"} 'db-scaling-factor conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid DB scaling factor 3. Must be one of 1, 2, 4, 8, 16."))

  (let [conf ["DB_BLOCK_SIZE" ce/coerce-db-block-size "16384"]]
    (given (ce/bindings-and-settings {} 'db-block-size conf)
      :bind-map := {'db-block-size 16384}
      [:settings "DB_BLOCK_SIZE" :value] := 16384
      [:settings "DB_BLOCK_SIZE" :default-value] := "16384")

    (given (ce/bindings-and-settings {"DB_BLOCK_SIZE" "10000"} 'db-block-size conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid DB block size 10000. Must be one of 4096, 8192, 16384, 32768, 65536, 131072.")))

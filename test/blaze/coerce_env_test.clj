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

  (let [conf ["JVM_METRICS_LOGGER_WARN_FACTOR" ce/jvm-metrics-logger-warn-factor "5"]]
    (given (ce/bindings-and-settings {} 'jvm-metrics-logger-warn-factor conf)
      :bind-map := {'jvm-metrics-logger-warn-factor 5}
      [:settings "JVM_METRICS_LOGGER_WARN_FACTOR" :value] := 5
      [:settings "JVM_METRICS_LOGGER_WARN_FACTOR" :default-value] := "5")

    (given (ce/bindings-and-settings {"JVM_METRICS_LOGGER_WARN_FACTOR" "10"} 'jvm-metrics-logger-warn-factor conf)
      :bind-map := {'jvm-metrics-logger-warn-factor 10}
      [:settings "JVM_METRICS_LOGGER_WARN_FACTOR" :value] := 10
      [:settings "JVM_METRICS_LOGGER_WARN_FACTOR" :default-value] := "5")

    (given (ce/bindings-and-settings {"JVM_METRICS_LOGGER_WARN_FACTOR" "0"} 'jvm-metrics-logger-warn-factor conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JVM metrics logger warn factor 0. Must be a positive integer."))

  (let [conf ["JVM_METRICS_LOGGER_WARN_THRESHOLD" ce/jvm-metrics-logger-warn-threshold "80"]]
    (given (ce/bindings-and-settings {} 'jvm-metrics-logger-warn-threshold conf)
      :bind-map := {'jvm-metrics-logger-warn-threshold 80}
      [:settings "JVM_METRICS_LOGGER_WARN_THRESHOLD" :value] := 80
      [:settings "JVM_METRICS_LOGGER_WARN_THRESHOLD" :default-value] := "80")

    (given (ce/bindings-and-settings {"JVM_METRICS_LOGGER_WARN_THRESHOLD" "1"} 'jvm-metrics-logger-warn-threshold conf)
      :bind-map := {'jvm-metrics-logger-warn-threshold 1}
      [:settings "JVM_METRICS_LOGGER_WARN_THRESHOLD" :value] := 1
      [:settings "JVM_METRICS_LOGGER_WARN_THRESHOLD" :default-value] := "80")

    (given (ce/bindings-and-settings {"JVM_METRICS_LOGGER_WARN_THRESHOLD" "0"} 'jvm-metrics-logger-warn-threshold conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JVM metrics logger threshold 0. Must be in the range from 1 (inclusive) to 100 (exclusive).")

    (given (ce/bindings-and-settings {"JVM_METRICS_LOGGER_WARN_THRESHOLD" "100"} 'jvm-metrics-logger-warn-threshold conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid JVM metrics logger threshold 100. Must be in the range from 1 (inclusive) to 100 (exclusive)."))

  (let [conf ["DB_SCALE_FACTOR" ce/coerce-db-scale-factor "1"]]
    (given (ce/bindings-and-settings {} 'db-scale-factor conf)
      :bind-map := {'db-scale-factor 1}
      [:settings "DB_SCALE_FACTOR" :value] := 1
      [:settings "DB_SCALE_FACTOR" :default-value] := "1")

    (given (ce/bindings-and-settings {"DB_SCALE_FACTOR" "4"} 'db-scale-factor conf)
      :bind-map := {'db-scale-factor 4}
      [:settings "DB_SCALE_FACTOR" :value] := 4
      [:settings "DB_SCALE_FACTOR" :default-value] := "1")

    (given (ce/bindings-and-settings {"DB_SCALE_FACTOR" "3"} 'db-scale-factor conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid DB scale factor 3. Must be one of 1, 2, 4, 8, 16."))

  (let [conf ["DB_BLOCK_SIZE" ce/coerce-db-block-size "16384"]]
    (given (ce/bindings-and-settings {} 'db-block-size conf)
      :bind-map := {'db-block-size 16384}
      [:settings "DB_BLOCK_SIZE" :value] := 16384
      [:settings "DB_BLOCK_SIZE" :default-value] := "16384")

    (given (ce/bindings-and-settings {"DB_BLOCK_SIZE" "10000"} 'db-block-size conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid DB block size 10000. Must be one of 4096, 8192, 16384, 32768, 65536, 131072.")))

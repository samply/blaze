(ns blaze.coerce-env-test
  (:require
   [blaze.coerce-env :as ce]
   [blaze.coerce-env-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
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
      ::anom/message := "Invalid DB block size 10000. Must be one of 4096, 8192, 16384, 32768, 65536, 131072."))

  (let [conf ["DB_RESOURCE_CACHE_SIZE_RATIO" ce/coerce-cache-size-ratio "0.25"]]
    (given (ce/bindings-and-settings {} 'resource-cache-size-ratio conf)
      :bind-map := {'resource-cache-size-ratio 0.25}
      [:settings "DB_RESOURCE_CACHE_SIZE_RATIO" :value] := 0.25
      [:settings "DB_RESOURCE_CACHE_SIZE_RATIO" :default-value] := "0.25")

    (given (ce/bindings-and-settings {"DB_RESOURCE_CACHE_SIZE_RATIO" "0"} 'resource-cache-size-ratio conf)
      :bind-map := {'resource-cache-size-ratio 0.0})

    (given (ce/bindings-and-settings {"DB_RESOURCE_CACHE_SIZE_RATIO" " 0.5 "} 'resource-cache-size-ratio conf)
      :bind-map := {'resource-cache-size-ratio 0.5})

    (testing "false disables the cache"
      (doseq [value ["false" " false "]]
        (given (ce/bindings-and-settings {"DB_RESOURCE_CACHE_SIZE_RATIO" value} 'resource-cache-size-ratio conf)
          :bind-map := {'resource-cache-size-ratio 0.0})))

    (testing "non-finite ratios are invalid"
      (doseq [value ["NaN" "Infinity" "-Infinity"]]
        (given (ce/bindings-and-settings {"DB_RESOURCE_CACHE_SIZE_RATIO" value} 'resource-cache-size-ratio conf)
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid non-finite cache size ratio %s." value))))

    (given (ce/bindings-and-settings {"DB_RESOURCE_CACHE_SIZE_RATIO" "-0.1"} 'resource-cache-size-ratio conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid negative cache size ratio -0.1.")

    (given (ce/bindings-and-settings {"DB_RESOURCE_CACHE_SIZE_RATIO" "foo"} 'resource-cache-size-ratio conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Can't parse a cache size ratio from `foo`."))

  (let [conf ["VALIDATOR_FAILURE_MODE" ce/coerce-validator-failure-mode "tag-outcome"]]
    (given (ce/bindings-and-settings {} 'validator-failure-mode conf)
      :bind-map := {'validator-failure-mode :tag-outcome}
      [:settings "VALIDATOR_FAILURE_MODE" :value] := :tag-outcome
      [:settings "VALIDATOR_FAILURE_MODE" :default-value] := "tag-outcome")

    (doseq [mode ["tag-only" "tag-only " "Tag-only" "TAG-ONLY" "TAG_ONLY"]]
      (given (ce/bindings-and-settings {"VALIDATOR_FAILURE_MODE" mode} 'validator-failure-mode conf)
        :bind-map := {'validator-failure-mode :tag-only}
        [:settings "VALIDATOR_FAILURE_MODE" :value] := :tag-only
        [:settings "VALIDATOR_FAILURE_MODE" :default-value] := "tag-outcome"))

    (doseq [mode ["tag-outcome" "tag-outcome " "Tag-outcome" "TAG-OUTCOME" "TAG_OUTCOME"]]
      (given (ce/bindings-and-settings {"VALIDATOR_FAILURE_MODE" mode} 'validator-failure-mode conf)
        :bind-map := {'validator-failure-mode :tag-outcome}
        [:settings "VALIDATOR_FAILURE_MODE" :value] := :tag-outcome
        [:settings "VALIDATOR_FAILURE_MODE" :default-value] := "tag-outcome"))

    (doseq [mode ["reject" "reject " "Reject" "REJECT"]]
      (given (ce/bindings-and-settings {"VALIDATOR_FAILURE_MODE" mode} 'validator-failure-mode conf)
        :bind-map := {'validator-failure-mode :reject}
        [:settings "VALIDATOR_FAILURE_MODE" :value] := :reject
        [:settings "VALIDATOR_FAILURE_MODE" :default-value] := "tag-outcome"))

    (given (ce/bindings-and-settings {"VALIDATOR_FAILURE_MODE" "foo"} 'validator-failure-mode conf)
      ::anom/category := ::anom/incorrect
      ::anom/message := "Invalid validator failure mode `foo`. Must be one of tag-only, tag-outcome or reject.")))

(defn- approx= [a b]
  (< (abs (- (double a) (double b))) 1e-9))

(defn- ratios [bind-map]
  ((juxt #(get % 'resource-cache-size-ratio)
         #(get % 'resource-handle-cache-size-ratio))
   bind-map))

(deftest limit-cache-size-ratios-test
  (testing "keeps ratios which fit together into 0.8"
    (let [bind-map {'resource-cache-size-ratio 0.25
                    'resource-handle-cache-size-ratio 0.1
                    'db-scale-factor 1}]
      (is (= bind-map (ce/limit-cache-size-ratios bind-map)))))

  (testing "lowers both ratios by the same amount if they exceed 0.8 together"
    (let [[rc rhc] (ratios (ce/limit-cache-size-ratios
                            {'resource-cache-size-ratio 0.6
                             'resource-handle-cache-size-ratio 0.4}))]
      (is (approx= 0.5 rc))
      (is (approx= 0.3 rhc))))

  (testing "constrains each ratio to 0.8 first"
    (let [[rc rhc] (ratios (ce/limit-cache-size-ratios
                            {'resource-cache-size-ratio 1.0
                             'resource-handle-cache-size-ratio 0.1}))]
      (is (approx= 0.75 rc))
      (is (approx= 0.05 rhc))))

  (testing "a disabled resource handle cache keeps the ratio of the resource
            cache"
    (let [bind-map {'resource-cache-size-ratio 0.8
                    'resource-handle-cache-size-ratio 0.0}]
      (is (= bind-map (ce/limit-cache-size-ratios bind-map)))))

  (testing "keeps the other bindings"
    (is (= 4 (get (ce/limit-cache-size-ratios
                   {'resource-cache-size-ratio 0.6
                    'resource-handle-cache-size-ratio 0.4
                    'db-scale-factor 4})
                  'db-scale-factor))))

  (testing "the limited ratios are never negative and fit together into 0.8"
    (satisfies-prop 1000
      (prop/for-all [rc (gen/double* {:min 0 :max 2 :NaN? false})
                     rhc (gen/double* {:min 0 :max 2 :NaN? false})]
        (let [[rc rhc] (ratios (ce/limit-cache-size-ratios
                                {'resource-cache-size-ratio rc
                                 'resource-handle-cache-size-ratio rhc}))]
          (and (<= 0 rc) (<= 0 rhc) (<= (+ rc rhc) (+ 0.8 1e-9))))))))

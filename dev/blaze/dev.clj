(ns blaze.dev
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.resource-cache :as resource-cache]
    [blaze.db.resource-store :as rs]
    [blaze.db.tx-log :as tx-log]
    [blaze.spec]
    [blaze.system :as system]
    [blaze.system-spec]
    [clojure.repl :refer [pst]]
    [clojure.spec.test.alpha :as st]
    [clojure.tools.namespace.repl :refer [refresh]]
    [criterium.core :refer [bench quick-bench]]
    [taoensso.timbre :as log]
    [blaze.db.hash :as hash]
    [cheshire.core :as cheshire]
    [blaze.executors :as ex])
  (:import
    [com.google.common.hash HashCode]
    [java.time Duration]))


;; Spec Instrumentation
(st/instrument)


(defonce system nil)


(defn init []
  (alter-var-root #'system (constantly (system/init! (System/getenv))))
  nil)


(defn reset []
  (some-> system system/shutdown!)
  (refresh :after `init))


;; Init Development
(comment
  (init)
  (pst)
  )


;; Reset after making changes
(comment
  (reset)
  (st/unstrument)
  )

(comment
  (log/set-level! :trace)
  (log/set-level! :debug)
  (log/set-level! :info)
  )

(comment
  (str (resource-cache/stats (:blaze.db/resource-cache system)))
  (resource-cache/invalidate-all! (:blaze.db/resource-cache system))
  )

;; Node
(comment
  (def node (:blaze.db/node system))
  (def db (d/db node))

  (into [] (map :id) (d/list-resources db "Patient"))

  (.hash (d/resource db "Patient" "01f5d727-e75c-4662-aecd-df2ffccd2e27"))

  @(blaze.db.node/load-tx-result node (:kv-store node) 21228)

  )

;; Kafka Transaction Log
(comment
  (def tx-log (::tx-log/kafka system))

  (with-open [queue (tx-log/new-queue tx-log 0)]
    (tx-log/poll queue (Duration/ofSeconds 1)))
  )

;; Cassandra Resource Store
(comment
  (def resource-store (::rs/cassandra system))

  (rs/get resource-store (HashCode/fromString "072e074677eae7a5cfa4408e870bf32d839d58bb2c59470c0a7f1eced74eb6d8"))
  )


(comment
  (require '[blaze.db.kv :as kv]
           '[blaze.db.hash :as hash]
           '[cheshire.core :as cheshire]
           '[blaze.async-comp :as ac]
           '[blaze.executors :as ex]
           '[criterium.core :refer [quick-bench bench]])

  (def patient-0 {:resourceType "Patient" :id "0"})
  (def patient-0-hash (hash/generate patient-0))

  (def observation-0 (binding [cheshire.parse/*use-bigdecimals?* true] (cheshire/parse-string (slurp "/Users/akiel/coding/bbmri-fhir-ig/input/examples/exampleBodyHeight.json") keyword)))
  (def observation-0-hash (hash/generate observation-0))

  (def kv-store (get system [:blaze.db.kv/rocksdb :blaze.db/resource-kv-store]))

  (kv/put kv-store (hash/encode patient-0-hash) (cheshire/generate-cbor patient-0))
  (kv/put kv-store (hash/encode observation-0-hash) (cheshire/generate-cbor observation-0))

  (defn get-async [kv-store hash executor]
    (ac/supply-async
      (fn []
        (cheshire/parse-cbor (kv/get kv-store (hash/encode hash)) keyword))
      executor))

  (defn get-sync [kv-store hash]
    (cheshire/parse-cbor (kv/get kv-store (hash/encode hash)) keyword))

  (def executor (ex/single-thread-executor))

  (bench @(get-async kv-store patient-0-hash executor))
  (bench (get-sync kv-store patient-0-hash))

  (bench @(get-async kv-store observation-0-hash executor))
  (bench (get-sync kv-store observation-0-hash))

  )

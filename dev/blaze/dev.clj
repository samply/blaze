(ns blaze.dev
  (:require
   [blaze.byte-string :as bs]
   [blaze.cache-collector.protocols :as ccp]
   [blaze.db.api :as d]
   [blaze.db.api-spec]
   [blaze.db.resource-cache :as rc]
   [blaze.db.resource-store :as rs]
   [blaze.db.tx-log :as tx-log]
   [blaze.elm.expression :as-alias expr]
   [blaze.spec]
   [blaze.system :as system]
   [blaze.system-spec]
   [blaze.test-util :as tu]
   [clojure.repl :refer [pst]]
   [clojure.spec.test.alpha :as st]
   [clojure.tools.namespace.repl :refer [refresh]]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(defonce system nil)

(defn init []
  (alter-var-root #'system (constantly (system/init! (System/getenv))))
  (st/instrument)
  (tu/set-default-locale-english!)
  nil)

(defn reset []
  (some-> system system/shutdown!)
  (refresh :after `init))

;; Init Development
(comment
  (init)
  system
  (pst))

;; Reset after making changes
(comment
  (reset)
  system
  (st/unstrument))

(comment
  (log/set-min-level! :trace)
  (log/set-min-level! :debug)
  (log/set-min-level! :info))

;; Transaction Cache
(comment
  (str (ccp/-stats (:blaze.db/tx-cache system)))
  (rc/invalidate-all! (:blaze.db/tx-cache system)))

;; Resource Cache
(comment
  (str (ccp/-stats (:blaze.db/resource-cache system)))
  (ccp/-estimated-size (:blaze.db/resource-cache system))
  (rc/invalidate-all! (:blaze.db/resource-cache system)))

;; CQL Expression Cache
(comment
  (str (ccp/-stats (::expr/cache system))))

;; RocksDB Stats
(comment
  (.reset (system [:blaze.db.kv.rocksdb/stats :blaze.db.index-kv-store/stats])))

;; Node
(comment
  (def node (:blaze.db/node system))
  (def db (d/db node)))

;; Kafka Transaction Log
(comment
  (def tx-log (::tx-log/kafka system))

  (with-open [queue (tx-log/new-queue tx-log 0)]
    (tx-log/poll! queue (time/seconds 1))))

;; Cassandra Resource Store
(comment
  (def resource-store (::rs/cassandra system))

  (rs/get resource-store ["Resource" (bs/from-iso-8859-1-string "072e074677eae7a5cfa4408e870bf32d839d58bb2c59470c0a7f1eced74eb6d8") :complete]))

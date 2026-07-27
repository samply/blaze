(ns blaze.interaction.test-util
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.kv :as kv]
   [blaze.db.node :as node]
   [blaze.db.tx-log :as tx-log]
   [blaze.handler.util :as handler-util])
  (:import
   [java.util.concurrent CountDownLatch]))

(set! *warn-on-reflection* true)

(def v3-ObservationValue
  "http://terminology.hl7.org/CodeSystem/v3-ObservationValue")

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defn coding
  ([system]
   (fn [codings]
     (filterv #(= system (:value (:system %))) codings)))
  ([codings system]
   (filterv #(= system (:value (:system %))) codings)))

(defn submit-tx-in-between
  "Submits `tx-ops` on `node`, holding the resulting transaction back from
  indexing until a second transaction was submitted, and calls `f`.

  Because a request handler reads the database before it submits its own
  transaction, a handler called in `f` is guaranteed to see the database state
  before `tx-ops` while its own transaction is indexed after that of `tx-ops`.

  The indexing is held back by blocking all writes to the index key-value store
  of `node`, because the point in time of `node` is only advanced after a
  transaction was written to that store."
  [node tx-ops f]
  (let [index-kv-store (:kv-store node)
        second-tx (CountDownLatch. 2)
        orig-submit tx-log/submit
        orig-put kv/put!]
    (with-redefs [tx-log/submit
                  (fn [tx-log tx-cmds local-payload]
                    (.countDown second-tx)
                    (orig-submit tx-log tx-cmds local-payload))
                  kv/put!
                  (fn [store entries]
                    (when (identical? index-kv-store store)
                      (.await second-tx))
                    (orig-put store entries))]
      @(node/submit-tx node tx-ops)
      (try
        (f)
        (finally
          ;; release the indexing in case no second transaction was submitted,
          ;; because otherwise closing the node would block forever
          (dotimes [_ 2] (.countDown second-tx)))))))

(defmacro with-tx-in-between
  "Evaluates `body` while the transaction with `tx-ops` submitted on `node` is
  held back from indexing until the handler called in `body` has submitted its
  own transaction."
  [[node tx-ops] & body]
  `(submit-tx-in-between ~node ~tx-ops (fn [] ~@body)))

(ns blaze.interaction.test-util
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.kv :as kv]
   [blaze.db.node :as node]
   [blaze.db.tx-log :as tx-log]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util])
  (:import
   [java.util.concurrent CountDownLatch]))

(set! *warn-on-reflection* true)

(def v3-ObservationValue
  "http://terminology.hl7.org/CodeSystem/v3-ObservationValue")

(def icd-10-gm
  "http://fhir.de/CodeSystem/bfarm/icd-10-gm")

(def icd-10-gm-code-system
  "A small ICD-10-GM code system, in which C71 is a child of C69-C72."
  {:fhir/type :fhir/CodeSystem :id "icd-10-gm"
   :url #fhir/uri "http://fhir.de/CodeSystem/bfarm/icd-10-gm"
   :content #fhir/code "fragment"
   :concept
   [{:fhir/type :fhir.CodeSystem/concept
     :code #fhir/code "C69-C72"}
    {:fhir/type :fhir.CodeSystem/concept
     :code #fhir/code "C71"
     :property
     [{:fhir/type :fhir.CodeSystem.concept/property
       :code #fhir/code "parent"
       :value #fhir/code "C69-C72"}]}
    {:fhir/type :fhir.CodeSystem/concept
     :code #fhir/code "C73"}]})

(def icd-10-gm-c69-c72-vcl
  "A value set of the ICD-10-GM codes C69-C72 and C71."
  "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/icd-10-gm)concept<<C69-C72")

(def icd-10-gm-empty-vcl
  "An empty value set, because C73 has no descendants."
  "http://fhir.org/VCL?v1=(http://fhir.de/CodeSystem/bfarm/icd-10-gm)concept<C73")

(def icd-10-gm-empty-vcl-msg
  "The message of the error of a condition with `icd-10-gm-empty-vcl`."
  (format "Error in search parameter `code:in`: The ValueSet `%s` expands to no codes. A condition with such a value set can't match any resource."
          icd-10-gm-empty-vcl))

(defn icd-10-gm-concept [code]
  (type/codeable-concept
   {:coding [(type/coding {:system (type/uri icd-10-gm) :code (type/code code)})]}))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defn type-query-failing-at
  "Returns a replacement of `d/type-query` that returns a fault with `msg` on
  databases with the point in time `t` and calls `type-query` otherwise.

  With `t` of the transaction of a request, only the queries on the database
  after the transaction fail, but not the queries of indexing the transaction,
  which use the database before."
  [type-query t msg]
  (fn [db type clauses]
    (if (= t (d/t db))
      (ba/fault msg)
      (type-query db type clauses))))

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
                  (fn [tx-log tx-cmds permissions local-payload]
                    (.countDown second-tx)
                    (orig-submit tx-log tx-cmds permissions local-payload))
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

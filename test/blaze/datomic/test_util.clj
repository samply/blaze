(ns blaze.datomic.test-util
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]))


(defn with-resource
  "Returns a tuple of a database with the given resource and its entity id.

  The resource id of `type`, has a logical `id` and possibly `more` elements.

  Example: `(with-resource db \"Patient\" \"0\")`"
  {:arglists '([db type id & more])}
  [db type id & {:as more}]
  (let [tid (d/tempid (keyword "part" type))
        {db :db-after :keys [tempids]}
        (d/with db [(merge {:db/id tid (util/resource-id-attr type) id
                            :instance/version -3} more)])
        id (d/resolve-tempid db tempids tid)]
    [db id]))


(defn with-deleted-resource
  [db type id]
  (let [tid (d/tempid (keyword "part" type))
        {db :db-after :keys [tempids]}
        (d/with db [{:db/id tid (util/resource-id-attr type) id
                     :instance/version -2}])
        id (d/resolve-tempid db tempids tid)]
    [db id]))


(defn with-non-primitive
  {:arglists '([db & more])}
  [db attr value & {:as more}]
  (let [tid (d/tempid (keyword "part" (namespace attr)))
        {db :db-after :keys [tempids]}
        (d/with db [(merge {:db/id tid attr value} more)])
        id (d/resolve-tempid db tempids tid)]
    [db id]))


(defn with-code
  ([db code]
   (with-code db nil nil code))
  ([db system code]
   (with-code db system nil code))
  ([db system version code]
   (let [tid (d/tempid :part/code)
         {db :db-after :keys [tempids]}
         (d/with db [(cond->
                       {:db/id tid
                        :code/id (str system "|" version "|" code)}
                       system
                       (assoc :code/system system)
                       version
                       (assoc :code/version version)
                       code
                       (assoc :code/code code))])
         id (d/resolve-tempid db tempids tid)]
     [db id])))


(defn with-gender-code [db gender]
  (with-code db "http://hl7.org/fhir/administrative-gender" gender))


(defn with-icd10-code [db version code]
  (with-code db "http://hl7.org/fhir/sid/icd-10" version code))



;; ---- datomic.api stubs -----------------------------------------------------

(defn stub-as-of [db t as-of-db]
  (st/instrument
    [`d/as-of]
    {:spec
     {`d/as-of
      (s/fspec
        :args (s/cat :db #{db} :t #{t})
        :ret #{as-of-db})}
     :stub
     #{`d/as-of}}))


(defn stub-basis-t [db t]
  (st/instrument
    [`d/basis-t]
    {:spec
     {`d/basis-t
      (s/fspec
        :args (s/cat :db #{db})
        :ret #{t})}
     :stub
     #{`d/basis-t}}))


(defn stub-db [conn db]
  (st/instrument
    [`d/db]
    {:spec
     {`d/db
      (s/fspec
        :args (s/cat :conn #{conn})
        :ret #{db})}
     :stub
     #{`d/db}}))


(defn stub-entity [db eid-spec entity-spec]
  (st/instrument
    [`d/entity]
    {:spec
     {`d/entity
      (s/fspec
        :args (s/cat :db #{db} :eid eid-spec)
        :ret entity-spec)}
     :stub
     #{`d/entity}}))


(defn stub-squuid [id]
  (st/instrument
    [`d/squuid]
    {:spec
     {`d/squuid
      (s/fspec
        :args (s/cat)
        :ret #{id})}
     :stub
     #{`d/squuid}}))


(defn stub-sync [conn t db]
  (st/instrument
    [`d/sync]
    {:spec
     {`d/sync
      (s/fspec
        :args (s/cat :conn #{conn} :t #{t})
        :ret #{db})}
     :stub
     #{`d/sync}}))



;; ---- blaze.datomic.util stubs ----------------------------------------------

(defn stub-basis-transaction [db transaction]
  (st/instrument
    [`util/basis-transaction]
    {:spec
     {`util/basis-transaction
      (s/fspec
        :args (s/cat :db #{db})
        :ret #{transaction})}
     :stub
     #{`util/basis-transaction}}))


(defn stub-cached-entity [db eid-spec entity-spec]
  (st/instrument
    [`util/cached-entity]
    {:spec
     {`util/cached-entity
      (s/fspec
        :args (s/cat :db #{db} :eid eid-spec)
        :ret entity-spec)}
     :stub
     #{`util/cached-entity}}))


(defn stub-deleted? [resource result-spec]
  (st/instrument
    [`util/deleted?]
    {:spec
     {`util/deleted?
      (s/fspec
        :args (s/cat :resource #{resource})
        :ret result-spec)}
     :stub
     #{`util/deleted?}}))


(defn stub-initial-version? [resource result-spec]
  (st/instrument
    [`util/initial-version?]
    {:spec
     {`util/initial-version?
      (s/fspec
        :args (s/cat :resource #{resource})
        :ret result-spec)}
     :stub
     #{`util/initial-version?}}))


(defn stub-initial-version-server-assigned-id? [resource result-spec]
  (st/instrument
    [`util/initial-version-server-assigned-id?]
    {:spec
     {`util/initial-version-server-assigned-id?
      (s/fspec
        :args (s/cat :resource #{resource})
        :ret result-spec)}
     :stub
     #{`util/initial-version-server-assigned-id?}}))


(defn stub-instance-transaction-history [db eid result]
  (st/instrument
    [`util/instance-transaction-history]
    {:spec
     {`util/instance-transaction-history
      (s/fspec
        :args (s/cat :db #{db} :eid #{eid})
        :ret #{result})}
     :stub
     #{`util/instance-transaction-history}}))


(defn stub-list-resources [db type resources-spec]
  (st/instrument
    `util/list-resources
    {:spec
     {`util/list-resources
      (s/fspec
        :args (s/cat :db #{db} :type #{type})
        :ret resources-spec)}
     :stub
     #{`util/list-resources}}))


(defn stub-literal-reference [resource result]
  (st/instrument
    [`util/literal-reference]
    {:spec
     {`util/literal-reference
      (s/fspec
        :args (s/cat :resource #{resource})
        :ret #{result})}
     :stub
     #{`util/literal-reference}}))


(defn stub-ordinal-version [resource result]
  (st/instrument
    `util/ordinal-version
    {:spec
     {`util/ordinal-version
      (s/fspec
        :args (s/cat :resource #{resource})
        :ret #{result})}
     :stub
     #{`util/ordinal-version}}))


(defn stub-resource [db type-spec id-spec entity-spec]
  (st/instrument
    [`util/resource]
    {:spec
     {`util/resource
      (s/fspec
        :args (s/cat :db #{db} :type type-spec :id id-spec)
        :ret entity-spec)}
     :stub
     #{`util/resource}}))


(defn stub-resource-type [resource type]
  (st/instrument
    [`util/resource-type]
    {:spec
     {`util/resource-type
      (s/fspec
        :args (s/cat :resource #{resource})
        :ret #{type})}
     :stub
     #{`util/resource-type}}))


(defn stub-tx-instant [transaction result]
  (st/instrument
    [`util/tx-instant]
    {:spec
     {`util/tx-instant
      (s/fspec
        :args (s/cat :transaction #{transaction})
        :ret #{result})}
     :stub
     #{`util/tx-instant}}))


(defn stub-type-transaction-history [db type result]
  (st/instrument
    [`util/type-transaction-history]
    {:spec
     {`util/type-transaction-history
      (s/fspec
        :args (s/cat :db #{db} :type #{type})
        :ret #{result})}
     :stub
     #{`util/type-transaction-history}}))


(defn stub-system-transaction-history [db result]
  (st/instrument
    [`util/system-transaction-history]
    {:spec
     {`util/system-transaction-history
      (s/fspec
        :args (s/cat :db #{db})
        :ret #{result})}
     :stub
     #{`util/system-transaction-history}}))



;; ---- blaze.datomic.pull stubs ----------------------------------------------

(defn stub-pull-resource [db type id resource-spec]
  (st/instrument
    [`pull/pull-resource]
    {:spec
     {`pull/pull-resource
      (s/fspec
        :args (s/cat :db #{db} :type #{type} :id #{id})
        :ret resource-spec)}
     :stub
     #{`pull/pull-resource}}))


(defn stub-pull-resource* [db type resource resource-spec]
  (st/instrument
    [`pull/pull-resource*]
    {:spec
     {`pull/pull-resource*
      (s/fspec
        :args (s/cat :db #{db} :type #{type} :resource #{resource})
        :ret resource-spec)}
     :stub
     #{`pull/pull-resource*}}))



;; ---- blaze.datomic.transaction stubs ---------------------------------------

(defn stub-resource-deletion [db type id result-spec]
  (st/instrument
    [`tx/resource-deletion]
    {:spec
     {`tx/resource-deletion
      (s/fspec
        :args (s/cat :db #{db} :type #{type} :id #{id})
        :ret result-spec)}
     :stub
     #{`tx/resource-deletion}}))


(defn stub-resource-type-total [db type result]
  (st/instrument
    [`util/resource-type-total]
    {:spec
     {`util/resource-type-total
      (s/fspec
        :args (s/cat :db #{db} :type #{type})
        :ret #{result})}
     :stub
     #{`util/resource-type-total}}))


(defn stub-resource-upsert [db tempids initial-version resource-spec result-spec]
  (st/instrument
    [`tx/resource-upsert]
    {:spec
     {`tx/resource-upsert
      (s/fspec
        :args (s/cat :db #{db} :tempids #{tempids}
                     :initial-version #{initial-version}
                     :resource resource-spec)
        :ret result-spec)}
     :stub
     #{`tx/resource-upsert}}))


(defn stub-transact-async [conn tx-data tx-result]
  (st/instrument
    [`tx/transact-async]
    {:spec
     {`tx/transact-async
      (s/fspec
        :args (s/cat :conn #{conn} :tx-data #{tx-data})
        :ret #{tx-result})}
     :stub
     #{`tx/transact-async}}))

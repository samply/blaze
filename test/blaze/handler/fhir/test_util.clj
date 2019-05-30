(ns blaze.handler.fhir.test-util
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as handler-fhir-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [datomic.api :as d]))


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


(defn stub-upsert-resource [conn db initial-version resource tx-result]
  (st/instrument
    [`handler-fhir-util/upsert-resource]
    {:spec
     {`handler-fhir-util/upsert-resource
      (s/fspec
        :args (s/cat :conn #{conn} :db #{db} :initial-version #{initial-version}
                     :resource #{resource})
        :ret #{tx-result})}
     :stub
     #{`handler-fhir-util/upsert-resource}}))

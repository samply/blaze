(ns life-fhir-store.datomic.coding
  (:require
    [clj-uuid :as uuid]
    [clojure.spec.alpha :as s]
    [datomic-spec.core :as ds]
    [datomic-tools.schema :refer [defattr defpart]]))


(defpart :life.part/Coding)


(defattr :Coding/id
  :db/valueType :db.type/uuid
  :db/cardinality :db.cardinality/one
  :db/unique :db.unique/identity)


(defattr :Coding/system
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :Coding/version
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :Coding/code
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one
  :db/index true)


(defattr :Coding/display
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one)


(defattr :Coding/userSelected
  :db/valueType :db.type/boolean
  :db/cardinality :db.cardinality/one)


(def ^:private namespace-coding-id
  #uuid "5377bbda-8411-4241-b54c-d1d6b6108b72")


(s/fdef id
  :args (s/cat :coding map?)
  :ret uuid?)

(defn id [{:keys [system version code]}]
  (cond-> namespace-coding-id
    system (uuid/v5 system)
    version (uuid/v5 version)
    code (uuid/v5 code)))


(s/fdef upsert
  :args (s/cat :tid ::ds/tempid :coding map?)
  :ret ::ds/tx-data)

(defn upsert
  [tid {:keys [system version code] :as coding}]
  [(cond-> {:db/id tid
            :Coding/id (id coding)}
     system (assoc :Coding/system system)
     version (assoc :Coding/version version)
     code (assoc :Coding/code code))])

(ns blaze.handler.fhir.util
  (:require
    [blaze.datomic.transaction :as tx]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [datomic-spec.core :as ds]))


(defn- tempids [db resource]
  (when-let [[type id tempid] (tx/resource-tempid db resource)]
    {type {id tempid}}))


(s/fdef upsert-resource
  :args (s/cat :conn ::ds/conn :db ::ds/db
               :creation-mode ::tx/creation-mode
               :resource ::tx/resource))

(defn upsert-resource
  "Throws exceptions with `ex-data` containing an anomaly on errors or
  unsupported features."
  [conn db creation-mode resource]
  (let [tempids (tempids db resource)
        tx-data (tx/resource-upsert db tempids creation-mode resource)]
    (if (empty? tx-data)
      {:db-after db}
      (tx/transact-async conn tx-data))))


(defn- remove-leading-slashes [url]
  (if (str/starts-with? url "/")
    (remove-leading-slashes (subs url 1))
    url))


(defn extract-type-and-id [url]
  (str/split (remove-leading-slashes url) #"/"))

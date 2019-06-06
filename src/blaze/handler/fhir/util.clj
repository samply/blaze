(ns blaze.handler.fhir.util
  (:require
    [blaze.datomic.transaction :as tx]
    [clojure.string :as str]))


(defn- tempids [db resource]
  (when-let [[type id tempid] (tx/resource-tempid db resource)]
    {type {id tempid}}))


(defn upsert-resource
  [conn db initial-version resource]
  (let [tempids (tempids db resource)
        tx-data (tx/resource-upsert db tempids initial-version resource)]
    (if (empty? tx-data)
      {:db-after db}
      (tx/transact-async conn tx-data))))


(defn- remove-leading-slashes [url]
  (if (str/starts-with? url "/")
    (remove-leading-slashes (subs url 1))
    url))


(defn extract-type-and-id [url]
  (str/split (remove-leading-slashes url) #"/"))

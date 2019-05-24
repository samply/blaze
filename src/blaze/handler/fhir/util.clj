(ns blaze.handler.fhir.util
  (:require
    [blaze.datomic.transaction :as tx]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [manifold.deferred :as md]))


(defn update-resource
  [conn resource & {:keys [max-retries] :or {max-retries 5}}]
  (md/loop [retried 0
            db (d/db conn)]
    (-> (tx/transact-async conn (tx/resource-update db resource))
        (md/catch'
          (fn [{::anom/keys [category] :as anomaly}]
            (if (and (< retried max-retries) (= ::anom/conflict category))
              (-> (d/sync conn (inc (d/basis-t db)))
                  (md/chain #(md/recur (inc retried) %)))
              (md/error-deferred anomaly)))))))


(defn- remove-leading-slashes [url]
  (if (str/starts-with? url "/")
    (remove-leading-slashes (subs url 1))
    url))


(defn extract-type-and-id [url]
  (str/split (remove-leading-slashes url) #"/"))

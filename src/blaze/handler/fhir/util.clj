(ns blaze.handler.fhir.util
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.transaction :as tx]
    [blaze.datomic.util :as util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [ring.util.response :as ring]
    [ring.util.time :as ring-time]))


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

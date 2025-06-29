(ns blaze.interaction.util
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.fhir.hash :as hash]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [clojure.string :as str]
   [ring.util.response :as ring]))

(defn etag->t [etag]
  (let [[_ t] (re-find #"W/\"(\d+)\"" etag)]
    (some-> t parse-long)))

(defn- prep-if-none-match [if-none-match]
  (if (= "*" if-none-match)
    :any
    (etag->t if-none-match)))

(defn- parse-if-match [if-match]
  (keep etag->t (str/split if-match #",")))

(defn- precondition-failed-msg [{:fhir/keys [type] :keys [id]} if-match]
  (if (str/blank? if-match)
    (format "Empty precondition failed on `%s/%s`." (name type) id)
    (format "Precondition `%s` failed on `%s/%s`." if-match (name type) id)))

(defn- update-tx-op-no-preconditions
  [db {:fhir/keys [type] :keys [id] :as resource}]
  (if-let [resource-handle (d/resource-handle db (name type) id)]
    (let [new-hash (hash/generate resource)]
      (if (= (:hash resource-handle) new-hash)
        [:keep (name type) id new-hash]
        [:put resource]))
    [:put resource]))

(defn- update-tx-op-if-match
  [db {:fhir/keys [type] :keys [id] :as resource} if-match ts]
  (if-let [resource-handle (d/resource-handle db (name type) id)]
    (let [new-hash (hash/generate resource)]
      (if (= (:hash resource-handle) new-hash)
        (let [t (:t resource-handle)]
          (cond
            (some #{t} ts)
            [:keep (name type) id new-hash (filterv (partial <= t) ts)]
            (every? (partial > t) ts)
            (ba/conflict (precondition-failed-msg resource if-match)
                         :http/status 412)
            :else
            [:put resource (into [:if-match] (filter (partial < t) ts))]))
        [:put resource (into [:if-match] ts)]))
    [:put resource (into [:if-match] ts)]))

(defn update-tx-op
  "Returns either a put or a keep tx-op with `resource` and possible
  preconditions from `if-match` and `if-none-match` or an anomaly."
  [db resource if-match if-none-match]
  (let [parsed-if-match (some-> if-match parse-if-match)
        if-none-match (some-> if-none-match prep-if-none-match)]
    (cond
      (and (some? parsed-if-match) (empty? parsed-if-match))
      (ba/conflict (precondition-failed-msg resource if-match)
                   :http/status 412)
      parsed-if-match (update-tx-op-if-match db resource if-match parsed-if-match)
      if-none-match [:put resource [:if-none-match if-none-match]]
      :else (update-tx-op-no-preconditions db resource))))

(defn strip-meta
  "Strips :versionId :lastUpdated from :meta of `resource`."
  {:arglists '([resource])}
  [{:keys [meta] :as resource}]
  (let [meta (into {} (keep (fn [[k v]] (when (and v (not (#{:versionId :lastUpdated} k))) [k v]))) meta)]
    (if (empty? meta)
      (dissoc resource :meta)
      (assoc resource :meta (type/meta meta)))))

(defn keep?
  "Determines whether `tx-op` is a keep operator."
  {:arglists '([tx-op])}
  [[op]]
  (identical? :keep op))

(defn wrap-invalid-id [handler]
  (fn [{{:keys [id]} :path-params :as request}]
    (cond
      (not (re-matches #"[A-Za-z0-9\-\.]{1,64}" id))
      (ac/completed-future
       (ba/incorrect
        (format "Resource id `%s` is invalid." id)
        :fhir/issue "value"
        :fhir/operation-outcome "MSG_ID_INVALID"))

      :else
      (handler request))))

(defn response [resource]
  (let [{:blaze.db/keys [tx]} (meta resource)]
    (-> (ring/response resource)
        (ring/header "Last-Modified" (fhir-util/last-modified tx))
        (ring/header "ETag" (fhir-util/etag tx)))))

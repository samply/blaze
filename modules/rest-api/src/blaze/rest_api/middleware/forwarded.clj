(ns blaze.rest-api.middleware.forwarded
  "A middleware that sets the base URL in the request either of one of the
  Forwarded headers or the default."
  (:refer-clojure :exclude [str])
  (:require
   [blaze.http.util :as hu]
   [blaze.util :refer [str]]))

(defn- name-value-map [m]
  (into {} (map (juxt :name :value)) m))

(defn- extract-host-and-proto-from-forwarded [{forwarded "forwarded"}]
  (when-let [[{:keys [name value params]}] (hu/parse-header-value forwarded)]
    (let [{:strs [host proto]} (assoc (name-value-map params) name value)]
      [host proto])))

(defn- extract-host-and-proto
  [{host "x-forwarded-host" proto "x-forwarded-proto" :as headers}]
  (if host
    [host proto]
    (extract-host-and-proto-from-forwarded headers)))

(defn- extract-base-url [headers]
  (let [[host proto] (extract-host-and-proto headers)]
    (when host
      (str (or proto "http") "://" host))))

(defn- forwarded-request [{:keys [headers] :as request} default-base-url]
  (assoc request :blaze/base-url (or (extract-base-url headers) default-base-url)))

(defn wrap-forwarded [handler default-base-url]
  (fn [request respond raise]
    (handler (forwarded-request request default-base-url) respond raise)))

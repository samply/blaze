(ns blaze.admin-api
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.kv.rocksdb :as rocksdb]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [reitit.ring]
   [reitit.ring.spec]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- rocksdb-column-families-handler [index-kv-store]
  (fn [_]
    (-> (ring/response
         {:column-families (mapv name (rocksdb/column-families index-kv-store))})
        (ac/completed-future))))

(defn- rocksdb-table-handler [index-kv-store]
  (fn [{{:keys [column-family]} :path-params}]
    (-> (ring/response {:tables (rocksdb/table-properties index-kv-store (keyword column-family))})
        (ac/completed-future))))

(defn- router [{:keys [context-path index-kv-store] :or {context-path ""}}]
  (reitit.ring/router
   ["/rocksdb"
    {}
    ["/index"
     {}
     ["/column-families"
      {}
      [""
       {:get (rocksdb-column-families-handler index-kv-store)}]
      ["/{column-family}"
       {}
       ["/tables"
        {:get (rocksdb-table-handler index-kv-store)}]]]]]
   {:path (str context-path "/__admin")
    :syntax :bracket}))

(defmethod ig/pre-init-spec :blaze/admin-api [_]
  (s/keys :req-un [:blaze/context-path :blaze.db/index-kv-store]))

(defmethod ig/init-key :blaze/admin-api
  [_ context]
  (log/info "Init Admin endpoint")
  (reitit.ring/ring-handler
   (router context)
   (fn [{:keys [uri]}]
     (-> (ring/not-found {:uri uri})
         (ac/completed-future)))))

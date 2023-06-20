(ns blaze.db.kv.rocksdb.protocols)

(defprotocol Rocks
  (-column-families [_])
  (-get-property [_ name] [_ column-family name])
  (-get-long-property [_ name] [_ column-family name])
  (-table-properties [_] [_ column-family]))

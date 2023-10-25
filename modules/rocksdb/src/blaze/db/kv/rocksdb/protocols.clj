(ns blaze.db.kv.rocksdb.protocols)

(defprotocol Rocks
  (-path [_])
  (-column-families [_])
  (-drop-column-family [_ column-family])
  (-property [_ name] [_ column-family name])
  (-long-property [_ name] [_ column-family name])
  (-map-property [_ name] [_ column-family name])
  (-agg-long-property [_ name])
  (-tables [_] [_ column-family])
  (-column-family-meta-data [_ column-family]))

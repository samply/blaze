(ns blaze.db.cache-collector.protocols)


(defprotocol StatsCache
  (-stats [_])
  (-estimated-size [_]))

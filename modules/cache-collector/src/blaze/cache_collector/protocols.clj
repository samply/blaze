(ns blaze.cache-collector.protocols)

(defprotocol StatsCache
  (-stats [_])
  (-estimated-size [_]))

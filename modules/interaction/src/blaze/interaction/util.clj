(ns blaze.interaction.util
  (:require
    [clojure.string :as str]))


(defn etag->t [etag]
  (when etag
    (let [[_ t] (re-find #"W/\"(\d+)\"" etag)]
      (when t
        (Long/parseLong t)))))


(defn clauses [query]
  (mapv
    #(let [[k v] (str/split % #"=")] (into [k] (str/split v #",")))
    (str/split query #"&")))

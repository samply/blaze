(ns blaze.rest-api.header
  (:require
   [clojure.string :as str]))

(defn- etag->tag [etag]
  (let [start (min (if (str/starts-with? etag "W/") 3 1) (count etag))]
    (subs etag start (max start (dec (count etag))))))

(defn if-none-match->tags
  "Converts an If-None-Match header `value` into a set of tags.

  Example: \"W/\\\"foo\\\", \\\"bar\\\"\" -> #{\"foo\" \"bar\"}"
  [value]
  (into #{} (map etag->tag) (some-> value (str/split #"\s*,\s*"))))

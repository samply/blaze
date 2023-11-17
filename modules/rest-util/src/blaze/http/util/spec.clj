(ns blaze.http.util.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :blaze.http.header.element.param/name
  string?)

(s/def :blaze.http.header.element.param/value
  string?)

(s/def :blaze.http.header.element/param
  (s/keys :req-un [:blaze.http.header.element.param/name
                   :blaze.http.header.element.param/value]))

(s/def :blaze.http.header.element/params
  (s/coll-of :blaze.http.header.element/param))

(s/def :blaze.http.header.element/name
  string?)

(s/def :blaze.http.header.element/value
  string?)

(s/def :blaze.http.header/element
  (s/keys :req-un [:blaze.http.header.element/name
                   :blaze.http.header.element/value]
          :opt-un [:blaze.http.header.element/params]))

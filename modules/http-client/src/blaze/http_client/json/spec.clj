(ns blaze.http-client.json.spec
  (:require
   [blaze.http-client.json :as-alias json]
   [clojure.spec.alpha :as s])
  (:import
   [com.fasterxml.jackson.databind ObjectMapper]
   [java.io InputStream]))

(s/def ::json/max-nesting-depth
  pos-int?)

(s/def ::json/max-number-length
  pos-int?)

(s/def ::json/max-name-length
  pos-int?)

(s/def ::json/max-string-length
  pos-int?)

(s/def ::json/max-document-length
  pos-int?)

(s/def ::json/constraints
  (s/keys :opt-un [::json/max-nesting-depth ::json/max-number-length
                   ::json/max-name-length ::json/max-string-length
                   ::json/max-document-length]))

(s/def ::json/object-mapper
  #(instance? ObjectMapper %))

(s/def ::json/status
  int?)

(s/def ::json/body
  #(instance? InputStream %))

(ns blaze.http-client.json-spec
  (:require
   [blaze.http-client.json :as json]
   [blaze.http-client.json.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom])
  (:import
   [java.io InputStream]))

(s/fdef json/object-mapper
  :args (s/cat :constraints ::json/constraints)
  :ret ::json/object-mapper)

(s/fdef json/parse-json
  :args (s/cat :mapper ::json/object-mapper
               :input-stream #(instance? InputStream %))
  :ret (s/or :anomaly ::anom/anomaly :value any?))

(s/fdef json/parse-response
  :args (s/cat :mapper ::json/object-mapper
               :response (s/keys :req-un [::json/status ::json/body]))
  :ret (s/or :anomaly ::anom/anomaly :value any?))

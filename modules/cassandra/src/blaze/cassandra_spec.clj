(ns blaze.cassandra-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.cassandra :as cass]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom])
  (:import
   [com.datastax.oss.driver.api.core CqlSession]
   [com.datastax.oss.driver.api.core.cql
    AsyncResultSet BoundStatement PreparedStatement SimpleStatement Statement]))

(s/fdef cass/prepare
  :args (s/cat :session #(instance? CqlSession %)
               :statement #(instance? SimpleStatement %))
  :ret #(instance? PreparedStatement %))

(s/fdef cass/bind
  :args (s/cat :prepared-statement #(instance? PreparedStatement %)
               :params (s/* any?))
  :ret #(instance? BoundStatement %))

(s/fdef cass/execute
  :args (s/cat :session #(instance? CqlSession %)
               :statement #(instance? Statement %))
  :ret ac/completion-stage?)

(s/fdef cass/first-row
  :args (s/cat :result-set #(instance? AsyncResultSet %))
  :ret (s/or :result bytes? :anomaly ::anom/anomaly))

(s/fdef cass/session
  :args (s/cat :config map?)
  :ret #(instance? CqlSession %))

(s/fdef cass/close
  :args (s/cat :session #(instance? CqlSession %)))

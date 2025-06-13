(ns blaze.interaction.util-spec
  (:require
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [blaze.fhir.spec.spec]
   [blaze.interaction.util :as iu]
   [blaze.spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef iu/etag->t
  :args (s/cat :etag string?)
  :ret (s/nilable :blaze.db/t))

(s/fdef iu/update-tx-op
  :args (s/cat :db :blaze.db/db :resource :fhir/Resource
               :if-match (s/nilable string?)
               :if-none-match (s/nilable string?))
  :ret (s/or :tx-op :blaze.db/tx-op :anomaly ::anom/anomaly))

(s/fdef iu/strip-meta
  :args (s/cat :resource :fhir/Resource)
  :ret :fhir/Resource)

(s/fdef iu/keep?
  :args (s/cat :tx-op (s/nilable :blaze.db/tx-op))
  :ret boolean?)

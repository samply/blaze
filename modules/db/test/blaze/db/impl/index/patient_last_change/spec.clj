(ns blaze.db.impl.index.patient-last-change.spec
  (:require
   [blaze.db.impl.index.patient-last-change :as-alias plc]
   [blaze.db.impl.index.patient-last-change.state :as-alias state]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/def ::state/type
  #{:building :current})

(s/def ::plc/state
  (s/keys :req-un [::state/type] :opt-un [:blaze.db/t]))

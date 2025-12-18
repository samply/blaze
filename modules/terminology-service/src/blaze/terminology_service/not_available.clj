(ns blaze.terminology-service.not-available
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.protocols :as p]
   [integrant.core :as ig]
   [taoensso.timbre :as log]))

(def ^:private unsupported-anom
  (ba/unsupported "Terminology operations are not supported. Please enable either the external or the internal terminology service."))

(defmethod ig/init-key ::ts/not-available
  [_ _]
  (log/info "Init not-available terminology service")
  (reify p/TerminologyService
    (-code-systems [_]
      (ac/completed-future unsupported-anom))

    (-code-system-validate-code [_ _]
      (ac/completed-future unsupported-anom))

    (-expand-value-set [_ _]
      (ac/completed-future unsupported-anom))

    (-value-set-validate-code [_ _]
      (ac/completed-future unsupported-anom))))

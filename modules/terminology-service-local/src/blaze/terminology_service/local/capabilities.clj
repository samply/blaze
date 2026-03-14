(ns blaze.terminology-service.local.capabilities
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.fhir.spec.type :as type]
   [blaze.terminology-service.local.code-system :as cs]))

(defn- code-system-version [{:keys [version]} default]
  (cond->
   {:fhir/type :fhir.TerminologyCapabilities.codeSystem/version
    :isDefault (type/boolean default)}
    version (assoc :code version)))

(defn code-systems [db]
  (do-sync [code-system-index (cs/list db)]
    (mapv
     (fn [[url [first-code-system & more]]]
       {:fhir/type :fhir.TerminologyCapabilities/codeSystem
        :uri (type/canonical url)
        :version
        (into
         [(code-system-version first-code-system true)]
         (map #(code-system-version % false))
         more)})
     code-system-index)))

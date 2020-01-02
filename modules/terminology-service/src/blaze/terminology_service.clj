(ns blaze.terminology-service)


(defprotocol TerminologyService
  (-expand-value-set [_ params]))


(defn terminology-service? [x]
  (satisfies? TerminologyService x))


(defn expand-value-set [terminology-service params]
  (-expand-value-set terminology-service params))

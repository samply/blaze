(ns blaze.terminology-service.protocols)

(defprotocol TerminologyService
  (-code-systems [_])
  (-code-system-lookup [_ params])
  (-code-system-validate-code [_ params])
  (-expand-value-set [_ params])
  (-value-set-validate-code [_ params]))

(ns blaze.terminology-service.protocols)

(defprotocol TerminologyService
  (-code-systems [_])
  (-code-system-validate-code [_ request])
  (-expand-value-set [_ request])
  (-value-set-validate-code [_ request]))

(ns blaze.terminology-service.protocols)

(defprotocol TerminologyService
  (-post-init [_ node])
  (-code-systems [_])
  (-code-system-validate-code [_ params])
  (-expand-value-set [_ params])
  (-value-set-validate-code [_ params]))

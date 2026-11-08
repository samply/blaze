(ns blaze.fhir.test-util.graph-definition-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.fhir.test-util.graph-definition :as g-tu]
   [blaze.fhir.test-util.graph-definition.spec :as spec]
   [clojure.spec.alpha :as s]))

(s/fdef g-tu/extension-start
  :args ::spec/extension-args
  :ret :fhir/Extension)

(s/fdef g-tu/extension-node
  :args ::spec/extension-args
  :ret :fhir/Extension)

(s/fdef g-tu/extension-link-source-id
  :args ::spec/extension-args
  :ret :fhir/Extension)

(s/fdef g-tu/extension-link-target-id
  :args ::spec/extension-args
  :ret :fhir/Extension)

(s/fdef g-tu/extension-link-params
  :args ::spec/extension-args
  :ret :fhir/Extension)

(s/fdef g-tu/extension-link-compartment
  :args ::spec/extension-args
  :ret :fhir/Extension)

(s/fdef g-tu/graph-def
  :args (s/cat :opts (s/keys* :opt-un [::g-tu/start ::g-tu/start-type ::g-tu/nodes ::g-tu/link]))
  :ret :fhir/GraphDefinition)

(s/fdef g-tu/node
  :args (s/cat :id (s/nilable string?) :type (s/nilable string?)
               :opts (s/keys* :opt-un [::g-tu/profile]))
  :ret :fhir/Extension)

(s/fdef g-tu/link
  :args (s/cat :source-id (s/nilable string?) :target-id (s/nilable string?)
               :opts (s/keys* :opt-un [::g-tu/params ::g-tu/path ::g-tu/compartment]))
  :ret :fhir.GraphDefinition/link)

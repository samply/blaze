(ns blaze.fhir.spec.type-spec
  (:require
   [blaze.fhir.spec.impl.xml :as xml]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.spec.type.system-spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom])
  (:import
   [blaze.fhir.spec.type Primitive]
   [java.time OffsetDateTime]))

(s/fdef type/to-xml
  :args (s/cat :x #(instance? Primitive %))
  :ret xml/element?)

(s/fdef type/references
  :args (s/cat :x any?)
  :ret (s/coll-of :blaze.fhir/literal-ref-tuple))

(s/fdef type/boolean
  :args (s/cat :value (s/alt :value boolean? :extended map?))
  :ret (s/or :value type/boolean? :anomaly ::anom/anomaly))

(s/fdef type/integer
  :args (s/cat :value (s/alt :value int? :extended map?))
  :ret (s/or :value type/integer? :anomaly ::anom/anomaly))

(s/fdef type/string
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/string?)

(s/fdef type/string-interned
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/string?)

(s/fdef type/decimal
  :args (s/cat :value (s/alt :value decimal? :extended map?))
  :ret type/decimal?)

(s/fdef type/uri
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/uri?)

(s/fdef type/uri-interned
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/uri?)

(s/fdef type/url
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/url?)

(s/fdef type/canonical
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/canonical?)

(s/fdef type/base64Binary
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/base64Binary?)

(s/fdef type/instant
  :args (s/cat :value (s/alt :value #(instance? OffsetDateTime %) :extended map?))
  :ret type/instant?)

(s/fdef type/date
  :args (s/cat :value (s/alt :value system/date? :extended map?))
  :ret type/date?)

(s/fdef type/dateTime
  :args (s/cat :value (s/alt :value system/date-time? :extended map?))
  :ret type/dateTime?)

(s/fdef type/time
  :args (s/cat :value (s/alt :value system/time? :extended map?))
  :ret type/time?)

(s/fdef type/code
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/code?)

(s/fdef type/oid
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/oid?)

(s/fdef type/id
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/id?)

(s/fdef type/markdown
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/markdown?)

(s/fdef type/unsignedInt
  :args (s/cat :value (s/alt :value int? :extended map?))
  :ret (s/or :value type/unsignedInt? :anomaly ::anom/anomaly))

(s/fdef type/positiveInt
  :args (s/cat :value (s/alt :value int? :extended map?))
  :ret (s/or :value type/positiveInt? :anomaly ::anom/anomaly))

(s/fdef type/uuid
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/uuid?)

(s/fdef type/xhtml
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret type/xhtml?)

(s/fdef type/address
  :args (s/cat :x map?))

(s/fdef type/age
  :args (s/cat :x map?))

(s/fdef type/annotation
  :args (s/cat :x map?))

(s/fdef type/attachment
  :args (s/cat :x map?))

(s/fdef type/bundle-entry-search
  :args (s/cat :x map?))

(s/fdef type/codeable-concept
  :args (s/cat :x map?))

(s/fdef type/coding
  :args (s/cat :x map?))

(s/fdef type/contact-detail
  :args (s/cat :x map?))

(s/fdef type/contact-point
  :args (s/cat :x map?))

(s/fdef type/contributor
  :args (s/cat :x map?))

(s/fdef type/count
  :args (s/cat :x map?))

(s/fdef type/data-requirement-code-filter
  :args (s/cat :x map?))

(s/fdef type/data-requirement-date-filter
  :args (s/cat :x map?))

(s/fdef type/data-requirement-sort
  :args (s/cat :x map?))

(s/fdef type/data-requirement
  :args (s/cat :x map?))

(s/fdef type/distance
  :args (s/cat :x map?))

(s/fdef type/dosage
  :args (s/cat :x map?))

(s/fdef type/dosage-dose-and-rate
  :args (s/cat :x map?))

(s/fdef type/duration
  :args (s/cat :x map?))

(s/fdef type/expression
  :args (s/cat :x map?))

(s/fdef type/human-name
  :args (s/cat :x map?))

(s/fdef type/identifier
  :args (s/cat :x map?))

(s/fdef type/meta
  :args (s/cat :x map?))

(s/fdef type/money
  :args (s/cat :x map?))

(s/fdef type/parameter-definition
  :args (s/cat :x map?))

(s/fdef type/period
  :args (s/cat :x map?))

(s/fdef type/quantity
  :args (s/cat :x map?))

(s/fdef type/range
  :args (s/cat :x map?))

(s/fdef type/ratio
  :args (s/cat :x map?))

(s/fdef type/reference
  :args (s/cat :x map?))

(s/fdef type/related-artifact
  :args (s/cat :x map?))

(s/fdef type/sampled-data
  :args (s/cat :x map?))

(s/fdef type/signature
  :args (s/cat :x map?))

(s/fdef type/timing-repeat
  :args (s/cat :x map?))

(s/fdef type/timing
  :args (s/cat :x map?))

(s/fdef type/trigger-definition
  :args (s/cat :x map?))

(s/fdef type/usage-context
  :args (s/cat :x map?))

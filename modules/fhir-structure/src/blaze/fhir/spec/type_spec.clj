(ns blaze.fhir.spec.type-spec
  (:require
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.spec.type.system-spec]
   [clojure.alpha.spec :as s2]
   [clojure.spec.alpha :as s]))

(s/fdef type/type
  :args (s/cat :x any?)
  :ret (s/nilable :fhir/type))

(s/fdef type/value
  :args (s/cat :x any?)
  :ret (s/nilable system/value?))

(s/fdef type/references
  :args (s/cat :x any?)
  :ret (s/coll-of :blaze.fhir/literal-ref-tuple))

(s/fdef type/boolean
  :args (s/cat :value (s/alt :value boolean? :extended map?))
  :ret (s/or :value type/boolean? :invalid s2/invalid?))

(s/fdef type/integer
  :args (s/cat :value (s/alt :value int? :extended map?))
  :ret (s/or :value type/integer? :invalid s2/invalid?))

(s/fdef type/integer64
  :args (s/cat :value (s/alt :value int? :extended map?))
  :ret (s/or :value type/integer? :invalid s2/invalid?))

(s/fdef type/string
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/string? :invalid s2/invalid?))

(s/fdef type/decimal
  :args (s/cat :value (s/alt :integer-value int? :decimal-value decimal? :extended map?))
  :ret (s/or :value type/string? :invalid s2/invalid?))

(s/fdef type/uri
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/uri? :invalid s2/invalid?))

(s/fdef type/url
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/url? :invalid s2/invalid?))

(s/fdef type/canonical
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/canonical? :invalid s2/invalid?))

(s/fdef type/instant
  :args (s/cat :value (s/alt :string-value string? :system-value system/date-time? :extended map?))
  :ret (s/or :value type/instant? :invalid s2/invalid?))

(s/fdef type/date
  :args (s/cat :value (s/alt :string-value string? :system-value system/date? :extended map?))
  :ret (s/or :value type/date? :invalid s2/invalid?))

(s/fdef type/dateTime
  :args (s/cat :value (s/alt :string-value string? :system-value system/date-time? :extended map?))
  :ret (s/or :value type/dateTime? :invalid s2/invalid?))

(s/fdef type/time
  :args (s/cat :value (s/alt :string-value string? :system-value system/time? :extended map?))
  :ret (s/or :value type/time? :invalid s2/invalid?))

(s/fdef type/code
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/code? :invalid s2/invalid?))

(s/fdef type/oid
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/oid? :invalid s2/invalid?))

(s/fdef type/id
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/id? :invalid s2/invalid?))

(s/fdef type/markdown
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/markdown? :invalid s2/invalid?))

(s/fdef type/unsignedInt
  :args (s/cat :value (s/alt :value int? :extended map?))
  :ret (s/or :value type/unsignedInt? :invalid s2/invalid?))

(s/fdef type/positiveInt
  :args (s/cat :value (s/alt :value int? :extended map?))
  :ret (s/or :value type/positiveInt? :invalid s2/invalid?))

(s/fdef type/uuid
  :args (s/cat :value (s/alt :value string? :extended map?))
  :ret (s/or :value type/uuid? :invalid s2/invalid?))

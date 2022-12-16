(ns blaze.db.impl.codec.date-spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [blaze.byte-string-spec]
    [blaze.db.api-spec]
    [blaze.db.impl.codec.date :as codec-date]
    [blaze.db.impl.codec.spec]
    [blaze.fhir.spec]
    [blaze.fhir.spec.type.system-spec]
    [blaze.fhir.spec.type.system.spec]
    [clojure.spec.alpha :as s]
    [clojure.test.check]))


(s/fdef codec-date/encode-lower-bound
  :args (s/cat :date-time :system/date-or-date-time)
  :ret byte-string?)


(s/fdef codec-date/encode-upper-bound
  :args (s/cat :date-time :system/date-or-date-time)
  :ret byte-string?)


(s/fdef codec-date/encode-range
  :args (s/cat :start (s/nilable :system/date-or-date-time)
               :end (s/? (s/nilable :system/date-or-date-time)))
  :ret byte-string?)


(s/fdef codec-date/lower-bound-bytes
  :args (s/cat :date-range byte-string?)
  :ret byte-string?)


(s/fdef codec-date/upper-bound-bytes
  :args (s/cat :date-range byte-string?)
  :ret byte-string?)

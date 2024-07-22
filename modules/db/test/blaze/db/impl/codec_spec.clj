(ns blaze.db.impl.codec-spec
  (:require
   [blaze.byte-buffer-spec]
   [blaze.byte-string :refer [byte-string?]]
   [blaze.byte-string-spec]
   [blaze.db.api-spec]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.codec.spec]
   [blaze.fhir.spec]
   [blaze.fhir.spec.type.system-spec]
   [blaze.fhir.spec.type.system.spec]
   [clojure.spec.alpha :as s]
   [clojure.test.check]))

;; ---- Identifier Functions --------------------------------------------------

(s/fdef codec/id-byte-string
  :args (s/cat :id :blaze.resource/id)
  :ret :blaze.db/id-byte-string)

(s/fdef codec/id-string
  :args (s/cat :id-byte-string :blaze.db/id-byte-string)
  :ret :blaze.resource/id)

;; ---- Other Functions -------------------------------------------------------

(s/fdef codec/tid
  :args (s/cat :type :fhir.resource/type)
  :ret :blaze.db/tid)

(s/fdef codec/tid->type
  :args (s/cat :tid :blaze.db/tid)
  :ret :fhir.resource/type)

(s/fdef codec/c-hash
  :args (s/cat :code string?)
  :ret int?)

(s/fdef codec/v-hash
  :args (s/cat :value string?)
  :ret byte-string?)

(s/fdef codec/tid-id
  :args (s/cat :type :blaze.db/tid :id :blaze.db/id-byte-string)
  :ret byte-string?)

(s/fdef codec/string
  :args (s/cat :string string?)
  :ret byte-string?)

(s/fdef codec/number
  :args (s/cat :number number?)
  :ret byte-string?)

(s/fdef codec/quantity
  :args (s/cat :unit (s/nilable string?) :value number?)
  :ret byte-string?)

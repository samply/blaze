(ns blaze.db.impl.search-param.date-spec
  (:require
   [blaze.db.impl.index.resource-search-param-value-spec]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.search-param.date :as spd]
   [blaze.fhir.spec.type.system-spec]
   [blaze.fhir.spec.type.system.spec]
   [clojure.spec.alpha :as s]))

(s/fdef spd/le-value
  :args (s/cat :date-time :system/date-or-date-time))

(s/fdef spd/ge-value
  :args (s/cat :date-time :system/date-or-date-time))

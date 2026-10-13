(ns blaze.db.impl.index.rts-as-of-test
  (:require
   [blaze.byte-buffer :as bb]
   [blaze.db.impl.codec :as codec]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.db.impl.index.resource-handle-spec]
   [blaze.db.impl.index.rts-as-of :as rts]
   [blaze.db.impl.index.rts-as-of-spec]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest]]
   [clojure.test.check.properties :as prop]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- decoded-handle
  "Returns the resource handle a reader decodes from the ResourceAsOf entry
  written by `rts/index-entries`."
  [tid id t hash num-changes op]
  (let [[[_ _ value]] (rts/index-entries tid id t hash num-changes op)]
    (rh/resource-handle! tid id t t (bb/wrap value))))

(def ^:private handle-keys
  [:fhir/type :tid :id :t :hash :num-changes :op])

(deftest resource-handle-test
  (satisfies-prop 100
    (prop/for-all [type (sg/elements ["Patient" "Observation" "Condition"])
                   id (s/gen :blaze.resource/id)
                   t (s/gen :blaze.db/t)
                   hash (s/gen :blaze.resource/hash)
                   num-changes (sg/large-integer* {:min 1 :max 1000000})
                   op (sg/elements [:create :put :delete])]
      (let [tid (codec/tid type)]
        (= ((apply juxt handle-keys)
            (decoded-handle tid id t hash num-changes op))
           ((apply juxt handle-keys)
            (rts/resource-handle tid id t hash num-changes op)))))))

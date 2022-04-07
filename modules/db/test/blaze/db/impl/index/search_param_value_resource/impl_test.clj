(ns blaze.db.impl.index.search-param-value-resource.impl-test
  (:require
    [blaze.byte-buffer :as bb]
    [blaze.byte-string :as bs]
    [blaze.db.impl.index.search-param-value-resource :as sp-vr]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.index.search-param-value-resource.impl :as impl]
    [blaze.test-util :refer [satisfies-prop]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [clojure.test.check.properties :as prop]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest id-size-test
  (satisfies-prop 1000
    (prop/for-all [c-hash (s/gen :blaze.db/c-hash)
                   tid (s/gen :blaze.db/tid)
                   value (s/gen :blaze.db/byte-string)
                   id (s/gen :blaze.db/id-byte-string)
                   hash (s/gen :blaze.resource/hash)]
      (let [buf (bb/wrap (sp-vr/encode-key c-hash tid value id hash))]
        (= (bs/size id) (impl/id-size buf) (apply impl/id-size [buf]))))))

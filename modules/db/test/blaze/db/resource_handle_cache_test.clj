(ns blaze.db.resource-handle-cache-test
  (:require
    [blaze.db.resource-handle-cache]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is]]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Cache]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/with-level :trace (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- cache [max-size]
  (-> (ig/init
        {:blaze.db/resource-handle-cache
         {:max-size max-size}})
      (:blaze.db/resource-handle-cache)))


(deftest init-test
  (is (instance? Cache (cache 0))))

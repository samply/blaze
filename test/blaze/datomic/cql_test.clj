(ns blaze.datomic.cql-test
  (:require
    [blaze.datomic.cql :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))

(defn stub-find-code [db system code res]
  (st/instrument
    `find-code
    {:spec
     {`find-code
      (s/fspec
        :args (s/cat :db #{db} :system #{system} :code #{code})
        :ret #{res})}
     :stub #{`find-code}}))

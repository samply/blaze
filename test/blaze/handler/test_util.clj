(ns blaze.handler.test-util
  (:require
    [clojure.test :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [reitit.core :as reitit]))


(defn stub-match-by-name [router name params match]
  (st/instrument
    [`reitit/match-by-name]
    {:spec
     {`reitit/match-by-name
      (s/fspec
        :args (s/cat :router #{router} :name #{name}
                     :params #{params})
        :ret #{match})}
     :stub
     #{`reitit/match-by-name}}))


(defn stub-match-by-path [router path match]
  (st/instrument
    [`reitit/match-by-path]
    {:spec
     {`reitit/match-by-path
      (s/fspec
        :args (s/cat :router #{router} :path #{path})
        :ret #{match})}
     :stub
     #{`reitit/match-by-path}}))

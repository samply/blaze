(ns blaze.elm.compiler.retrieve-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [blaze.elm.compiler.retrieve :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]))


(deftest single-code-expr-test
  (datomic-test-util/stub-entid ::db :Patient.Observation.code/code-id 42)
  (datomic-test-util/stub-datoms
    ::db :eavt (s/cat :e #{::patient-eid} :a #{42})
    (constantly [{:v ::observation-eid}]))
  (datomic-test-util/stub-entity ::db #{::observation-eid} #{::observation})

  (is
    (= [::observation]
       (-eval
         (single-code-expr
           ::db "Patient" "Observation" "code" {:code/id "code-id"})
         {:db ::db}
         {:db/id ::patient-eid}
         nil))))


(defn stub-single-code-expr [db context data-type property code expr]
  (st/instrument
    [`single-code-expr]
    {:spec
     {`single-code-expr
      (s/fspec
        :args (s/cat :db #{db} :context #{context} :data-type #{data-type}
                     :property #{property} :code #{code})
        :ret #{expr})}
     :stub
     #{`single-code-expr}}))



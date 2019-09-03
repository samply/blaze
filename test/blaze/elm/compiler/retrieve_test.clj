(ns blaze.elm.compiler.retrieve-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.compiler.retrieve :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all])
  (:import
    [datomic Datom]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(deftest single-code-expr-test
  (st/unstrument `single-code-expr)
  (datomic-test-util/stub-entid ::db :Patient.Observation.code/code-id 42)
  (datomic-test-util/stub-datoms
    ::db :eavt (s/cat :e #{::patient-eid} :a #{42})
    (constantly [(reify Datom (v [_] ::observation-eid))]))
  (datomic-test-util/stub-entity ::db #{::observation-eid} #{::observation})

  (is
    (=
      (-eval
        (single-code-expr
          ::db "Patient" "Observation" "code" {:code/id "code-id"})
        {:db ::db}
        {:db/id ::patient-eid}
        nil)
      [::observation])))


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


(deftest multiple-code-expr-test
  (st/unstrument `multiple-code-expr)
  (st/instrument
    [`single-code-expr]
    {:spec
     {`single-code-expr
      (s/fspec
        :args
        (s/cat
          :db #{::db} :context #{"Patient"} :data-type #{"Observation"}
          :property #{"code"} :code #{::code-1 ::code-2}))}
     :replace
     {`single-code-expr
      (fn [_ _ _ _ code]
        (case code
          ::code-1 [::observation-1]
          ::code-2 [::observation-2]))}})

  (is
    (=
      (-eval
        (multiple-code-expr
          ::db "Patient" "Observation" "code"
          [::code-1 ::code-2])
        {:db ::db}
        {:db/id ::patient-eid}
        nil)
      [::observation-1
       ::observation-2])))


(deftest context-expr-test
  (st/unstrument `context-expr)

  (testing "Observation in Patient Context"
    (datomic-test-util/stub-entid ::db :Observation/subject 42)
    (datomic-test-util/stub-datoms
      ::db :vaet (s/cat :v #{::patient-eid} :a #{42})
      (constantly [(reify Datom (e [_] ::observation-eid))]))
    (datomic-test-util/stub-entity ::db #{::observation-eid} #{::observation})

    (is
      (=
        (-eval
          (context-expr ::db "Patient" "Observation")
          {:db ::db}
          {:db/id ::patient-eid}
          nil)
        [::observation])))

  (testing "Patient in Specimen Context"
    (datomic-test-util/stub-entid ::db :Specimen/subject 42)
    (datomic-test-util/stub-datoms
      ::db :eavt (s/cat :e #{::specimen-eid} :a #{42})
      (constantly [(reify Datom (v [_] ::patient-eid))]))
    (datomic-test-util/stub-entity ::db #{::patient-eid} #{::patient})

    (is
      (=
        (-eval
          (context-expr ::db "Specimen" "Patient")
          {:db ::db}
          {:db/id ::specimen-eid}
          nil)
        [::patient])))

  (testing "Observation in Specimen Context"
    (datomic-test-util/stub-entid ::db :Observation/specimen 42)
    (datomic-test-util/stub-datoms
      ::db :vaet (s/cat :v #{::specimen-eid} :a #{42})
      (constantly [(reify Datom (e [_] ::observation-eid))]))
    (datomic-test-util/stub-entity ::db #{::observation-eid} #{::observation})

    (is
      (=
        (-eval
          (context-expr ::db "Specimen" "Observation")
          {:db ::db}
          {:db/id ::specimen-eid}
          nil)
        [::observation]))))


(defn stub-context-expr [db context data-type expr]
  (st/instrument
    [`context-expr]
    {:spec
     {`context-expr
      (s/fspec
        :args (s/cat :db #{db} :context #{context} :data-type #{data-type})
        :ret #{expr})}
     :stub
     #{`context-expr}}))

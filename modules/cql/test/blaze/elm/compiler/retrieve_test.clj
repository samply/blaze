(ns blaze.elm.compiler.retrieve-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.compiler.retrieve
     :refer
     [context-expr
      expr
      multiple-code-expr
      single-code-expr
      with-related-context-expr]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]])
  (:import
    [datomic Datom]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


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


(defn replace-single-code-expr
  [db context-spec data-type-spec property-spec code-spec replace-fn]
  (st/instrument
    [`single-code-expr]
    {:spec
     {`single-code-expr
      (s/fspec
        :args
        (s/cat
          :db #{db} :context context-spec :data-type data-type-spec
          :property property-spec :code code-spec))}
     :replace
     {`single-code-expr replace-fn}}))


(deftest multiple-code-expr-test
  (st/unstrument `multiple-code-expr)
  (replace-single-code-expr
    ::db #{"Patient"} #{"Observation"} #{"code"} #{::code-1 ::code-2}
    (fn [_ _ _ _ code]
      (case code
        ::code-1 [::observation-1]
        ::code-2 [::observation-2])))

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

  (testing "Observation in Patient context"
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

  (testing "Patient in Specimen context"
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

  (testing "Observation in Specimen context"
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
        [::observation])))

  (testing "Unknown context"
    (try
      (context-expr ::db "foo" "bar")
      (catch Exception e
        (given (ex-data e)
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported execution context `foo`."
          :elm/expression-execution-context := "foo"))))

  (testing "Unknown data type in context Specimen"
    (try
      (context-expr ::db "Specimen" "foo")
      (catch Exception e
        (given (ex-data e)
          ::anom/category := ::anom/unsupported
          ::anom/message := "Unsupported data type `foo` in context `Specimen`."
          :elm/expression-execution-context := "Specimen")))))


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


(deftest related-context-expr-test-1
  (st/unstrument `with-related-context-expr)

  (testing "without codes"
    (datomic-test-util/stub-entity-type
      ::related-resource ::related-resource-type)
    (stub-context-expr
      ::db ::related-resource-type ::result-type
      (reify Expression
        (-eval [_ _ resource _]
          (is (= ::related-resource resource))
          ::result)))

    (let [related-context-expr
          (reify Expression
            (-eval [_ _ resource _]
              (is (= ::initial-resource resource))
              ::related-resource))
          expr (with-related-context-expr
                 related-context-expr ::result-type nil nil)]
      (is (= ::result (-eval expr {:db ::db} ::initial-resource nil))))))


(deftest related-context-expr-test-2
  (st/unstrument `with-related-context-expr)

  (testing "with one code"
    (datomic-test-util/stub-entity-type
      ::related-resource ::related-resource-type)
    (stub-single-code-expr
      ::db ::related-resource-type ::result-type ::code-property ::code
      (reify Expression
        (-eval [_ _ resource _]
          (is (= ::related-resource resource))
          ::result)))

    (let [related-context-expr
          (reify Expression
            (-eval [_ _ resource _]
              (is (= ::initial-resource resource))
              ::related-resource))
          expr (with-related-context-expr
                 related-context-expr ::result-type ::code-property [::code])]
      (is (= ::result (-eval expr {:db ::db} ::initial-resource nil))))))


(defn stub-multiple-codes-expr [db context data-type property codes expr]
  (st/instrument
    [`multiple-code-expr]
    {:spec
     {`multiple-code-expr
      (s/fspec
        :args (s/cat :db #{db} :context #{context} :data-type #{data-type}
                     :property #{property} :codes #{codes})
        :ret #{expr})}
     :stub
     #{`multiple-code-expr}}))


(deftest related-context-expr-test-3
  (st/unstrument `with-related-context-expr)

  (testing "with two codes"
    (datomic-test-util/stub-entity-type
      ::related-resource ::related-resource-type)
    (stub-multiple-codes-expr
      ::db ::related-resource-type ::result-type ::code-property
      [::code-1 :code-2]
      (reify Expression
        (-eval [_ _ resource _]
          (is (= ::related-resource resource))
          ::result)))

    (let [related-context-expr
          (reify Expression
            (-eval [_ _ resource _]
              (is (= ::initial-resource resource))
              ::related-resource))
          expr (with-related-context-expr
                 related-context-expr ::result-type ::code-property
                 [::code-1 :code-2])]
      (is (= ::result (-eval expr {:db ::db} ::initial-resource nil))))))


(deftest related-context-expr-test-4
  (st/unstrument `with-related-context-expr)

  (testing "with one nil code"
    (let [expr (with-related-context-expr
                 ::related-context-expr ::result-type ::code-property [nil])]
      (is (= [] (-eval expr {:db ::db} ::initial-resource nil))))))


(deftest expr-test-1
  (st/unstrument `expr)

  (testing "in non-Unspecified eval context"
    (testing "while retrieving resources of the same type as the context"
      (let [expr (expr "Patient" ::db "Patient" nil nil)]
        (testing "a singleton list of the current patient is returned"
          (is (= [::patient] (-eval expr {:db ::db} ::patient nil))))))

    (testing "while retrieving resources of a different type as the context"
      (stub-context-expr
        ::db ::context ::type
        (reify Expression
          (-eval [_ _ resource _]
            (is (= ::context-resource resource))
            ::result)))

      (let [expr (expr ::context ::db ::type nil nil)]
        (testing "the observations of the current patient are returned"
          (is (= ::result (-eval expr {:db ::db} ::context-resource nil))))))

    (testing "while retrieving resources of a different type as the context and one code"
      (stub-single-code-expr
        ::db ::context ::type ::code-property ::code
        (reify Expression
          (-eval [_ _ resource _]
            (is (= ::context-resource resource))
            ::result)))

      (let [expr (expr ::context ::db ::type ::code-property [::code])]
        (testing "the observations with that code of the current patient are returned"
          (is (= ::result (-eval expr {:db ::db} ::context-resource nil))))))

    (testing "while retrieving resources of a different type as the context and one not existing code"
      (let [expr (expr "Patient" ::db "Observation" "code" [nil])]
        (testing "a static empty list is returned"
          (is (= [] expr)))))

    (testing "while retrieving resources of a different type as the context and two codes"
      (stub-multiple-codes-expr
        ::db ::context ::type ::code-property [::code-1 ::code-2]
        (reify Expression
          (-eval [_ _ resource _]
            (is (= ::context-resource resource))
            ::result)))

      (let [expr (expr ::context ::db ::type ::code-property [::code-1 ::code-2])]
        (testing "the observations with that code of the current patient are returned"
          (is (= ::result (-eval expr {:db ::db} ::context-resource nil))))))))


(defn stub-related-context-expr
  [context-expr data-type code-property-name codes-spec res]
  (st/instrument
    [`with-related-context-expr]
    {:spec
     {`with-related-context-expr
      (s/fspec
        :args (s/cat :context-expr #{context-expr}
                     :data-type #{data-type}
                     :code-property-name #{code-property-name}
                     :codes codes-spec)
        :ret #{res})}
     :stub
     #{`with-related-context-expr}}))

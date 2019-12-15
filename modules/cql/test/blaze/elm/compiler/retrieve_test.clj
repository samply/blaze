(ns blaze.elm.compiler.retrieve-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.elm.compiler.protocols :refer [Expression -eval]]
    [blaze.elm.compiler.retrieve
     :refer
     [context-expr
      expr
      multiple-codes-expr
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

  (testing "evaluation with existing index"
    (datomic-test-util/stub-entid ::db :Patient.Observation.code/s1|c1 #{42})
    (datomic-test-util/stub-datoms-fn
      ::db :eavt (s/cat :e #{::patient-eid} :a #{42})
      (constantly [(reify Datom (v [_] ::observation-eid))]))
    (datomic-test-util/stub-entity ::db #{::observation-eid} #{::observation})

    (is
      (=
        (-eval
          (single-code-expr
            ::db "Patient" "Observation" "code" {:system "s1" :code "c1"})
          {:db ::db}
          {:db/id ::patient-eid}
          nil)
        [::observation])))

  (testing "non-existing index compiles to an empty list"
    (datomic-test-util/stub-entid ::db :Patient.Observation.code/s1|c1 nil?)
    (is
      (empty?
        (single-code-expr
          ::db "Patient" "Observation" "code" {:system "s1" :code "c1"})))))


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
  (st/unstrument `multiple-codes-expr)
  (replace-single-code-expr
    ::db #{"Patient"} #{"Observation"} #{"code"} #{::code-1 ::code-2}
    (fn [_ _ _ _ code]
      (case code
        ::code-1 [::observation-1]
        ::code-2 [::observation-2])))

  (is
    (=
      (-eval
        (multiple-codes-expr
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
    (datomic-test-util/stub-entid ::db :Reference.Observation/subject #{42})
    (datomic-test-util/stub-datoms-fn
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
    (datomic-test-util/stub-entid ::db :Reference.Specimen/subject #{42})
    (datomic-test-util/stub-datoms-fn
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
    (datomic-test-util/stub-entid ::db :Reference.Observation/specimen #{42})
    (datomic-test-util/stub-datoms-fn
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
    [`multiple-codes-expr]
    {:spec
     {`multiple-codes-expr
      (s/fspec
        :args (s/cat :db #{db} :context #{context} :data-type #{data-type}
                     :property #{property} :codes #{codes})
        :ret #{expr})}
     :stub
     #{`multiple-codes-expr}}))


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
      (let [expr (expr ::type ::db ::type nil nil)]
        (testing "a singleton list of the current patient is returned"
          (is (= [::instance] (-eval expr {:db ::db} ::instance nil))))))

    (testing "while retrieving resources of a different type as the context"
      (testing "without codes"
        (stub-context-expr
          ::db ::context ::type
          (reify Expression
            (-eval [_ _ resource _]
              (is (= ::context-resource resource))
              ::result)))

        (let [expr (expr ::context ::db ::type nil nil)]
          (is (= ::result (-eval expr {:db ::db} ::context-resource nil)))))

      (testing "with one code"
        (stub-single-code-expr
          ::db ::context ::type ::code-property ::code
          (reify Expression
            (-eval [_ _ resource _]
              (is (= ::context-resource resource))
              ::result)))

        (let [expr (expr ::context ::db ::type ::code-property [::code])]
          (is (= ::result (-eval expr {:db ::db} ::context-resource nil)))))

      (testing "with two codes"
        (stub-multiple-codes-expr
          ::db ::context ::type ::code-property [::code-1 ::code-2]
          (reify Expression
            (-eval [_ _ resource _]
              (is (= ::context-resource resource))
              ::result)))

        (let [expr (expr ::context ::db ::type ::code-property [::code-1 ::code-2])]
          (is (= ::result (-eval expr {:db ::db} ::context-resource nil))))))))


(defn stub-with-related-context-expr
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


(defn stub-expr
  [eval-context db data-type code-property-name codes-spec res]
  (st/instrument
    [`expr]
    {:spec
     {`expr
      (s/fspec
        :args (s/cat :eval-context #{eval-context}
                     :db #{db}
                     :data-type #{data-type}
                     :code-property-name #{code-property-name}
                     :codes codes-spec)
        :ret #{res})}
     :stub
     #{`expr}}))

(ns blaze.elm.compiler.property-test
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.elm.compiler.property :refer :all]
    [blaze.elm.compiler.protocols :refer [-eval]]
    [clojure.test :refer :all]))


(deftest attr-test
  (are [elm kw] (= kw (attr elm))
    {:resultTypeName "{http://hl7.org/fhir}Specimen.Collection"
     :path "collection"
     :scope "S"
     :type "Property"
     :life/scopes #{"S"}
     :life/source-type "{http://hl7.org/fhir}Specimen"}
    :Specimen/collection

    {:path "collected"
     :type "Property"
     :resultTypeSpecifier
     {:type "ChoiceTypeSpecifier"
      :choice [{:name "{http://hl7.org/fhir}dateTime"
                :type "NamedTypeSpecifier"}
               {:name "{http://hl7.org/fhir}Period"
                :type "NamedTypeSpecifier"}]}
     :source
     {:resultTypeName "{http://hl7.org/fhir}Specimen.Collection"
      :path "collection"
      :scope "S"
      :type "Property"
      :life/scopes #{"S"}
      :life/source-type "{http://hl7.org/fhir}Specimen"}}
    :Specimen.collection/collected))


(deftest scope-expr-test
  (is
    (=
      (-eval
        (scope-expr "C" :CodeableConcept/coding)
        nil
        nil
        {"C" {:CodeableConcept/coding "foo"}})
      "foo")))


(deftest scope-runtime-type-expr-test
  (let [entity {:CodeableConcept/coding "foo"}]
    (datomic-test-util/stub-entity-type entity "CodeableConcept")
    (is
      (=
        (-eval
          (scope-runtime-type-expr "C" "coding")
          nil
          nil
          {"C" entity})
        "foo"))))

(ns blaze.elm.compiler.comparison-operators
  "12. Comparison Operators

  Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
   [blaze.elm.compiler.core :as core]
   [blaze.elm.compiler.macros :refer [defbinop]]
   [blaze.elm.protocols :as p]))

;; 12.1. Equal
(defbinop equal [operand-1 operand-2 ^:now now]
  (p/equal operand-1 operand-2 now))

;; 12.2. Equivalent
(defbinop equivalent [operand-1 operand-2 ^:now now]
  (p/equivalent operand-1 operand-2 now))

;; 12.3. Greater
(defbinop greater [operand-1 operand-2 ^:now now]
  (p/greater operand-1 operand-2 now))

;; 12.4. GreaterOrEqual
(defbinop greater-or-equal [operand-1 operand-2 ^:now now]
  (p/greater-or-equal operand-1 operand-2 now))

;; 12.5. Less
(defbinop less [operand-1 operand-2 ^:now now]
  (p/less operand-1 operand-2 now))

;; 12.6. LessOrEqual
(defbinop less-or-equal [operand-1 operand-2 ^:now now]
  (p/less-or-equal operand-1 operand-2 now))

;; 12.7. NotEqual
(defmethod core/compile* :elm.compiler.type/not-equal
  [_ _]
  (throw (Exception. "Unsupported NotEqual expression. Please normalize the ELM tree before compiling.")))

library Retrieve version '1.0'
using FHIR version '3.0.0'

context Patient
context Population

define OnePlusOne: 1 + 1
define OnePointOnePlusOnePointOne: 1.1 + 1.1
define Year2019PlusOneYear: @2019 + 1 year
define OneYearPlusOneYear: 1 year + 1 year
define OneYearPlusOneMonth: 1 year + 1 month
define OneSecondPlusOneSecond: 1 second + 1 second
define OnePointOneSecondPlusOnePointOneSecond: 1.1 second + 1.1 second
define Time: @T12:30
define Date: Date(2019 + 1, 4, 9)
define IntegerMaximum: maximum Integer
define DateTimeMaximum: maximum DateTime

package default

import scala.concurrent.duration._
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.http.HeaderNames.ContentType
import io.gatling.http.Predef._

object Common {
  val contentTypeCheck = header(ContentType).is("application/fhir+json;charset=utf-8")

  def resourceTypeCheck(resourceType: Expression[String]) =
    jsonPath("$.resourceType").is(resourceType)
}

object Patient {

  val genderCheck = jsonPath("$.gender").is("${gender}")

  // produces $patient-id
  val create = feed(csv("default/patients.csv").eager.random)
    .exec(http("create-patient")
      .post("/fhir/Patient")
      .headers(Map("Content-Type" -> "application/fhir+json"))
      .body(ElFileBody("default/patient.json"))
      .check(status.is(201))
      .check(Common.contentTypeCheck)
      .check(Common.resourceTypeCheck("Patient"))
      .check(jsonPath("$.id").saveAs("patient-id"))
      .check(genderCheck))

  val get = doIf(session => session.contains("patient-id")) {
    exec(http("get-patient")
      .get("/fhir/Patient/${patient-id}")
      .check(status.is(200))
      .check(Common.contentTypeCheck)
      .check(Common.resourceTypeCheck("Patient"))
      .check(genderCheck)
    )
  }
}

object Observation {

  val bmiCheck = jsonPath("$.valueQuantity.value").is("${bmi}")

  val create = doIf(session => session.contains("patient-id")) {
    exec(http("create-observation-bmi")
      .post("/fhir/Observation")
      .headers(Map("Content-Type" -> "application/fhir+json"))
      .body(ElFileBody("default/observation-bmi.json"))
      .check(status.is(201))
      .check(Common.contentTypeCheck)
      .check(Common.resourceTypeCheck("Observation"))
      .check(jsonPath("$.id").saveAs("observation-id"))
      .check(bmiCheck))
  }

  val get = doIf(session => session.contains("observation-id")) {
    exec(http("get-observation")
      .get("/fhir/Observation/${observation-id}")
      .check(status.is(200))
      .check(Common.contentTypeCheck)
      .check(Common.resourceTypeCheck("Observation"))
      .check(bmiCheck)
    )
  }
}

object Library {

  val create = exec(http("create-library")
    .post("/fhir/Library")
    .headers(Map("Content-Type" -> "application/fhir+json"))
    .body(ElFileBody("default/library.json"))
    .check(status.is(201))
    .check(Common.contentTypeCheck)
    .check(Common.resourceTypeCheck("Library"))
    .check(jsonPath("$.id").saveAs("library-id")))

}

object Measure {

  val create = exec(http("create-measure")
    .post("/fhir/Measure")
    .headers(Map("Content-Type" -> "application/fhir+json"))
    .body(ElFileBody("default/measure.json"))
    .check(status.is(201))
    .check(Common.contentTypeCheck)
    .check(Common.resourceTypeCheck("Measure"))
    .check(jsonPath("$.id").saveAs("measure-id")))

  val evaluate = exec(http("evaluate-measure")
    .get("/fhir/Measure/$evaluate-measure")
    .queryParam("measure", "measure")
    .queryParam("periodStart", "2000")
    .queryParam("periodEnd", "2030")
    .check(status.is(200))
    .check(Common.contentTypeCheck)
    .check(Common.resourceTypeCheck("MeasureReport"))
    .check(jsonPath("$.type").is("summary"))
    .check(jsonPath("$.group[0].population[0].count").ofType[Int].gt(0)))
}

class BlazeSimulation extends Simulation {

  val httpProtocol = http
    //.baseUrl("http://ci-w1:8080")
    .baseUrl("https://blaze.test.life.uni-leipzig.local")
    .disableAutoReferer
    .acceptHeader("application/fhir+json")
    .shareConnections

  val createMeasure = scenario("Create Library and Measure")
    .exec(Library.create, Measure.create)
    .inject(atOnceUsers(1))

  val createPatients = scenario("Create Patients with one Observation")
    .exec(Patient.create)
    .exec(Observation.create)
    .inject(
      constantUsersPerSec(150) during (15 minutes)
      //constantConcurrentUsers(20) during (5 minutes)
    )

  val evaluateMeasures = scenario("Evaluate Measures")
    .exec(Measure.evaluate)
    .inject(
      //constantUsersPerSec(4) during (15 minutes)
      //heavisideUsers(1200) during (5 minutes)
      constantConcurrentUsers(16) during (15 minutes)
    )

  setUp(createMeasure,
    //createPatients,
    evaluateMeasures
  )
    .protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile4.lt(250),
      global.failedRequests.count.is(0)
    )
}

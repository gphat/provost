import github.gphat.Experiment

import java.util.concurrent.Executors
import org.specs2.mutable.Specification
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExperimentSpec extends Specification {

  def fastOK = {
    Future {
      Thread.sleep(250)
      "OK"
    }
  }

  def fastFail = {
    Future {
      Thread.sleep(250)
      throw new Exception("EEK!")
    }
  }

  def slowOK = {
    Future {
      Thread.sleep(1000)
      "OK"
    }
  }

  "Experiment" should {

    "handle normal cases" in {
      val ex = new Experiment[String](control = slowOK, candidate = fastOK)
      ex.perform must beEqualTo("OK").await(timeout = Duration(2000, "millis"))
    }

    "silently handle candidate failures" in {
      val ex = new Experiment[String](control = slowOK, candidate = fastFail)
      ex.perform must beEqualTo("OK").await(timeout = Duration(2000, "millis"))
    }

    "handle control failures" in {
      val ex = new Experiment[String](control = fastFail, candidate = slowOK)
      ex.perform must throwA[Exception].await(timeout = Duration(2000, "millis"))
    }

    "handle entire experiment" in {
      val ex = new Experiment[String](control = fastOK, candidate = slowOK)
      ex.perform
      ex.getFuture must beTrue.await(timeout = Duration(2000, "millis"))
      ex.getControl.isCompleted must beTrue
      ex.getCandidate.isCompleted must beTrue

      ex.getControl.value must beSome.which(_.isSuccess)
      ex.getCandidate.value must beSome.which(_.isSuccess)
    }

    "use supplied execution context" in {
      val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
      val ex = new Experiment[String](control = slowOK, candidate = fastOK)(ec)
      ex.perform must beEqualTo("OK").await(timeout = Duration(2000, "millis"))
    }
  }
}

import github.gphat.Experiment

import java.util.concurrent.Executors
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.TryValues
import org.scalatest.time.{Millis, Seconds, Span}

class ExperimentSpec extends FlatSpec with Matchers with ScalaFutures {

  implicit val defaultPatience = PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  def fastOK = {
    Future {
      Thread.sleep(250)
      "OK"
    }
  }

  def fastFail = {
    Future {
      Thread.sleep(125)
      throw new Exception("EEK!")
    }
  }

  def slowOK = {
    Future {
      Thread.sleep(500)
      "OK"
    }
  }

  "Experiment" should "handle normal cases" in {

    val ex = new Experiment[String](name = Some("better_string"), control = slowOK, candidate = fastOK)
    whenReady(ex.perform)({ res =>
      res should be ("OK")
    })
  }

  it should "silently handle candidate failures" in {
    val ex = new Experiment[String](name = Some("better_string"), control = slowOK, candidate = fastFail)
    whenReady(ex.perform)({ res =>
      res should be("OK")
    })
  }

  it should "handle control failures" in {
    val ex = new Experiment[String](name = Some("better_string"), control = fastFail, candidate = slowOK)
    whenReady(ex.perform.failed)({ res =>
      res shouldBe a [Exception]
    })

    whenReady(ex.getTotalFuture)({ res =>
      res.succeeded should be (false)
    })
  }

  it should "handle entire experiment" in {
    val ex = new Experiment[String](name = Some("better_string"), control = fastOK, candidate = slowOK)

    whenReady(ex.perform)({ res =>
      res should be("OK")
    })

    // Only the control is done (assuming this doesn't get flakey and we handle
    // it fast enough!)
    ex.control.isCompleted should be (true)
    ex.candidate.isCompleted should be (false)

    // We have to wait longer for the experiment to complete.
    whenReady(ex.candidate)({ res =>
      res should be ("OK")
    })

    whenReady(ex.getTotalFuture)({ res =>
      res.succeeded should be (true)
      res.controlDuration.toMillis.toInt should be >= 125
      res.candidateDuration.toMillis.toInt should be >= 500
      res.candidateDuration.toMillis.toInt should be >= res.controlDuration.toMillis.toInt

      res.equalled should be (true)
    })
  }

  it should "use supplied execution context" in {
    val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
    val ex = new Experiment[String](control = slowOK, candidate = fastOK)(ec)
    whenReady(ex.perform)({ res =>
      res should be("OK")
    })
  }
}

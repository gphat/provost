package github.gphat

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext,Future,Promise}
import scala.concurrent.duration._
import scala.util.Try

case class Result[A](
  name: Option[String],
  control: Future[A],
  candidate: Future[A],
  controlDuration: Duration,
  candidateDuration: Duration,
  controlResult: Try[A],
  candidateResult: Try[A],
  succeeded: Boolean
)

class Experiment[A](
  val name: Option[String] = None,
  val control: Future[A],
  val candidate: Future[A]
)(implicit xc: ExecutionContext = ExecutionContext.global) {
  // Keep up with how many have completed
  val counter = new AtomicInteger(2)

  // times
  val experimentBegin = System.currentTimeMillis
  var controlEnd: Option[Long] = None
  var candidateEnd: Option[Long] = None

  // Promises to watch
  val promise = Promise[A]()
  val experimentPromise = Promise[Result[A]]()

  def getTotalFuture = experimentPromise.future

  def perform = {
    // Install a handler on both futures
    control.onComplete(measureControl)
    candidate.onComplete(measureCandidate)

    // Tie our returned future to the control
    promise.completeWith(control)

    // Give 'em the future
    promise.future
  }

  def measureCandidate(result: Try[A]) = {
    candidateEnd = Some(System.currentTimeMillis)
    measure
  }

  def measureControl(result: Try[A]) = {
    controlEnd = Some(System.currentTimeMillis)
    measure
  }

  def measure = {
    counter.synchronized {
      // Increment our counter. If we hit zero then
      // we're done and can complete the experiment.
      if(counter.decrementAndGet == 0) {
        val controlResult = control.value.get
        val candidateResult = candidate.value.get

        experimentPromise.success(Result[A](
          name = name,
          control = control,
          candidate = candidate,
          // Safe cuz we know we set both end times above!
          controlDuration = Duration(controlEnd.get - experimentBegin, MILLISECONDS),
          candidateDuration = Duration(candidateEnd.get - experimentBegin, MILLISECONDS),
          controlResult = controlResult,
          candidateResult = candidateResult,
          succeeded = control.value.map({ _.isSuccess }).getOrElse(false)
            && candidate.value.map({ _.isSuccess }).getOrElse(false)
        ))
      }
    }
  }
}

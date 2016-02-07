package github.gphat

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext,Future,Promise}
import scala.concurrent.duration._
import scala.util.Try

/** The result of an experiment.
  *
  * Please note that the Result does not test equality of the underlying `Future`'s
  * results, it merely checks that the `Try`s therein didn't fail.
  *
  * @constructor Create a new result
  * @param name the name of the experiment that generated this result
  * @param control the future for the control
  * @param candidate the future for the candidate
  * @param controlDuration the amount of time for the control to complete
  * @param candidateDuration the amount of time for the candidate to complete
  * @param succeeded convenience boolean that signals if both future's underlying `Try`s were successful
  */
case class Result[A](
  name: Option[String],
  control: Future[A],
  candidate: Future[A],
  controlDuration: Duration,
  candidateDuration: Duration,
  succeeded: Boolean
) {
  /** Determine if our result equal each other. Note that this only work if the
    * equals method works for the type `A`.
    */
  def equalled = succeeded && control.value.get.equals(candidate.value.get)
}

/** An experiment!
  *
  * @constructor Create a new experiment
  * @param name an optional name, good for naming metrics or emitting logs, help future you remember what this is!
  * @param control the control future that you want to verify against
  * @param candidateEnd the candidate future you are testing out
  * @param xc an optional execution context, uses `ExeuectionContext.global` by default
  */
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

  /** Get a Future that is completed when both the control and candidate have
    * completed.
    */
  def getTotalFuture = experimentPromise.future

  /** Begin the experiment, returning a Future that completes when the control
    * completes so that you can return a result from it regardless of how slow
    * or fast the candidate might be.
    */
  def perform = {
    // Install a handler on both futures
    control.onComplete(measureControl)
    candidate.onComplete(measureCandidate)

    // Tie our returned future to the control
    promise.completeWith(control)

    // Give 'em the future
    promise.future
  }

  private def measureCandidate(result: Try[A]) = {
    candidateEnd = Some(System.currentTimeMillis)
    measure
  }

  private def measureControl(result: Try[A]) = {
    controlEnd = Some(System.currentTimeMillis)
    measure
  }

  private def measure = {
    counter.synchronized {
      // Increment our counter. If we hit zero then
      // we're done and can complete the experiment.
      if(counter.decrementAndGet == 0) {

        experimentPromise.success(Result[A](
          name = name,
          control = control,
          candidate = candidate,
          // Safe cuz we know we set both end times above!
          controlDuration = Duration(controlEnd.get - experimentBegin, MILLISECONDS),
          candidateDuration = Duration(candidateEnd.get - experimentBegin, MILLISECONDS),
          succeeded = control.value.map({ _.isSuccess }).getOrElse(false)
            && candidate.value.map({ _.isSuccess }).getOrElse(false)
        ))
      }
    }
  }
}

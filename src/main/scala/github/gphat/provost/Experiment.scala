package github.gphat

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext,Future,Promise}
import scala.util.Try

class Experiment[A](
  control: Future[A],
  candidate: Future[A]
)(implicit xc: ExecutionContext = ExecutionContext.global) {
  // Keep up with how many have completed
  val counter = new AtomicInteger(2)
  // Promises to watch
  val promise = Promise[A]()
  val candidatePromise = Promise[Boolean]()

  def getControl = control

  def getCandidate = candidate

  def getFuture = candidatePromise.future

  def perform = {
    // Install a handler on both futures
    control.onComplete(measure)
    candidate.onComplete(measure)

    // Tie our returned future to the control
    promise.completeWith(control)

    // Give 'em the future
    promise.future
  }

  def measure(result: Try[A]) = {
    this.synchronized {
      if(counter.decrementAndGet == 0) {
        candidatePromise.success(true)
      }
    }
  }
}

package github.gphat

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext,Future,Promise}
import scala.util.Try

class Experiment[A](
  control: Future[A],
  experiment: Future[A]
)(implicit xc: ExecutionContext = ExecutionContext.global) {
  // Keep up with how many have completed
  val counter = new AtomicInteger(2)
  // Promises to watch
  val promise = Promise[A]()
  val experimentPromise = Promise[Boolean]()

  def getControl = control

  def getExperiment = experiment

  def getFuture = experimentPromise.future

  def perform = {
    // Install a handler on both futures
    control.onComplete(measure)
    experiment.onComplete(measure)

    // Tie our returned future to the control
    promise.completeWith(control)

    // Give 'em the future
    promise.future
  }

  def measure(result: Try[A]) = {
    this.synchronized {
      if(counter.decrementAndGet == 0) {
        experimentPromise.success(true)
      }
    }
  }
}
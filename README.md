[![Build Status](https://travis-ci.org/gphat/provost.svg?branch=master)](https://travis-ci.org/gphat/provost)

Provost is a Scala library inspired by GitHub's
[scientist](https://github.com/github/scientist) and aims to allow easy side
by side testing of code paths using Futures.

# Features

* Asynchronous by way of Futures
* Returns when the control Future completes so that the other codepath won't slow things down

# Usage

Provost uses Futures. An **experiment** and a **control** Future must be
provided. The `perform` method returns a Future that will be completed when the
control completes and it will return the result of the control Future.

```scala
import github.gphat.Experiment

// Some futures!
def fastOK = {
  Future {
    Thread.sleep(250)
    "OK"
  }
}

def slowOK = {
  Future {
    Thread.sleep(1000)
    "OK"
  }
}

// Make an experiment! Note that the experiment is parameterized to the type
// we expect from our experiments. We provide a future for the control and
// for the candidate! Names are optional.
val ex = new Experiment[String](name = Some("better_string"), control = slowOK, candidate = fastOK)
val result = ex.perform

// The returned Future is tied to the control and will return even if
// the candidate hasn't finished yet.
ex.map({ r =>
  // Do something!
  println(r) // This is the output of the control!
})

// You can also get a Future that is tied to *both* Futures completing.
// It returns a Result and you can block/await/callback it's completion.
val wholeExperiment = ex.getTotalFuture
wholeExperiment.map({ result =>
  // Now you can look at the two futures and compare them or whatever
  val control = ex.control
  val candidate = ex.candidate

  // Both Futures are complete
  control.isCompleted // True!
  candidate.isCompleted // True!

  // You can test if both underlying Trys were successful.
  result.succeeded

  // You can compare the execution times of each. Note that these times will be
  // measured from the start of the experiment to each Future's completion! This
  // means we might be missing some time since you created these Futures by
  // yourself! Note that these are Scala Duration objects.
  result.candidateDuration
  result.controlDuration

  // You can inspect the two results to determine equality, which Provost leaves
  // to you to do since equality is hard. These will be Try[A]. You can use the
  // aforementioned `succeeded` to determine how to unwind the Try
  result.candidateResult
  result.candidateResult

  // So, as an example, maybe you'd resolve the whole thing like this:
  val control = oldFunction // A Future[String] that we know works
  val candidate = newFunction // A Future[String] that we're testing

  val ex = Experiment[String](name = Some("better_string"), control = control, candidate = candidate)

  // Assuming that your type can be compared with `equals`, you can use this!
  if(ex.equalled) {
    println("Yay, equality!")
  } else {
    println("Boo, we didn't equal up :(")
  }

  // See below
  ex.getTotalFuture.onComplete(experimentLogger)

  ex.perform.map({ res =>
    // Do whatever you were gonna do with the control's result, since it's
    // now in `res`!
  })

  // A more thorough walk through!
  def experimentLogger(result: Result[String]) = {
    if(fullResult.isSuccess) {
      if(ex.equalled) {
        println("Yay, equality!")
      } else {
        println("Boo, we didn't equal up :(")
      }
    } else {
      if(fullResult.controlResult.isFailure && fullResult.candidateResult.isFailure) {
        println("Both the candidate and the control failed, maybe that's ok?")
      if(fullResult.control.isFailure) {
        println("The control failed and the candidate succeeded. Maybe it's better!")
      } else {
        println("The candidate failed! Back to the drawing board!")
      }
    }
    // Now we can report on duration
    println("Control took ${fullResult.controlDuration.toMillis} ms")
    println("Candidate took ${fullResult.candidateDuration.toMillis} ms")

    // You could even emit these as metrics using the Experiment's name!
    statsd.time("experiment.${ex.name.get}.control.duration_ms", fullResult.controlDuration.toMillis)
    statsd.time("experiment.${ex.name.get}.candidate.duration_ms", fullResult.candidateDuration.toMillis)
  }

})

```

# Execution Contexts

You can also supply your own execution context. Provost uses the default Scala
global execution context otherwise.

```scala
val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
val ex = new Experiment[String](control = slowOK, candidate = fastOK)(ec)
```

# Internals

The Future returned by `perform` uses `completeWith` to tie itself to the provided control future. An `onComplete`
is added to both the control and the experiment and an `AtomicInteger` in a `this.synchronized` block is used
to track the completion of the two Futures. When both are complete the "whole experiment" Future is completed
with `success(true)`.

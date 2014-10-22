[![Build Status](https://travis-ci.org/gphat/provost.svg?branch=master)](https://travis-ci.org/gphat/provost)

Provost is a Scala library inspired by GitHub's
[dat-science](https://github.com/github/dat-science) and aims to allow easy side
by side testing of code paths using Futures.

# Features

* Asynchronous by way of Futures
* Returns when the control Future completes so that the other codepath won't slow things down

# Usage

Provost uses Futures. An experiment and a control Future must be provided. The `perform` method returns a Future that
will be completed when the control completes and it will return the result of the control Future.

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

// Make an experiment
val ex = new Experiment[String](control = slowOK, experiment = fastOK)
val result = ex.perform

// You can also supply your own execution context. Provost uses the
// default scala global execution context otherwise.
val ex2 = new Experiment[String](control = slowOK, experiment = fastOK)()

// The returned future is tied to the control and will return even if
// the experiment hasn't finished yet.
result.map({ r =>
  // Do something!
})

// You can also get a future that is tied to *both* future's completing.
// It only returns true, but you can block/await/callback it's completion
val wholeExperiment = ex.getFuture
wholeExperiment.map({ result =>
  // Now you can look at the two futures and compare them or whatever
  val control = ex.getControl
  val experiment = ex.getExperiment

  control.isCompleted // True!
  experiment.isCompleted // True!

  control.value.map({ c =>
    experiment.value.map({ e =>
      c // This will be a Try!
      e // This will be a Try!
      // Here you can test equality or whatever you need to do
      // to verify that your experimental code path worked.
    })
  })
})

```

# Internals

The Future returned by `perform` uses `completeWith` to tie itself to the provided control future. An `onComplete`
is added to both the control and the experiment and an `AtomicInteger` in a `this.synchronized` block is used
to track the completion of the two Futures. When both are complete the "whole experiment" Future is completed
with `success(true)`.

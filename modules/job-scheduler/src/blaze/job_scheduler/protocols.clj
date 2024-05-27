(ns blaze.job-scheduler.protocols)

(defprotocol JobHandler
  (-on-start [job-handler job]
    "Handles the event that a job was started.

    The jobs status will be `ready` and should be set to `in-progress` with the
    reason `started` if it can be started.

    Returns a CompletableFuture that will complete with a possibly updated job
    or will complete exceptionally with an anomaly in case of errors.")

  (-on-resume [job-handler job]
    "Handles the event that a job was resumed.

    The jobs status will be in-progress/resumed and should be set to
    in-progress/incremented on the next increment.

    Returns a CompletableFuture that will complete with a possibly updated job
    or will complete exceptionally with an anomaly in case of errors.")

  (-on-cancel [job-handler job]
    "Handles the event that the cancellation of `job` was requested.

    The jobs status will be `cancelled` with a sub status of `requested`. Should
    free the resources the job allocated.

    Returns a CompletableFuture that will complete with a possibly updated job
    or will complete exceptionally with an anomaly in case of errors."))

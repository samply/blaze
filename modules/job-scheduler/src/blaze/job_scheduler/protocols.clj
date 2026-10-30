(ns blaze.job-scheduler.protocols)

(defprotocol JobHandler
  (-on-start [job-handler job]
    "Handles the event that a job was started.

    The jobs status will be `ready` and should be set to `in-progress` with the
    reason `started` if it can be started.

    Returns a CompletableFuture that will complete with the job when its
    execution ends or will complete exceptionally with an anomaly if its
    execution fails. So the future spans the whole execution of the job, not
    only the update of its status. The job scheduler fails the job if the
    future completes exceptionally, unless the job was unable to update itself,
    e.g. because it was paused. On shutdown, the job scheduler waits for the
    future to complete.")

  (-on-resume [job-handler job]
    "Handles the event that a job was resumed.

    The jobs status will be in-progress/resumed and should be set to
    in-progress/incremented on the next increment.

    Returns a CompletableFuture like the one of `-on-start`, that spans the rest
    of the execution of the job.")

  (-on-cancel [job-handler job]
    "Handles the event that the cancellation of `job` was requested.

    The jobs status will be `cancelled` with a sub status of `requested`.
    Should only signal the running execution of `job` to stop. The work of the
    cancellation, like freeing the resources the job allocated and finishing
    the cancellation, belongs to the execution, so to the future returned by
    `-on-start` or `-on-resume`, which the job scheduler waits for on shutdown.

    Returns a CompletableFuture that should complete promptly with a possibly
    updated job or complete exceptionally with an anomaly in case of errors.
    The job scheduler doesn't wait for it on shutdown.")

  (-on-pause [job-handler job]
    "Handles the event that `job` was paused while it is running.

    The jobs status will be `on-hold` with the reason `paused`. Should make the
    running job stop as soon as possible, so that it can be resumed, because a
    job can't be resumed until its previous execution has ended. The job must
    stay on-hold, so it must not be updated.

    Must not block. The return value is ignored.")

  (-on-shutdown [job-handler job]
    "Handles the event that the job scheduler shuts down while `job` is
    running.

    The jobs status will be `on-hold` with the reason `orderly-shutdown`, or
    with the reason `paused` if the job was paused before but is still
    running. Should make the running job stop as soon as possible, because the
    job
    scheduler waits for the future returned by `-on-start` or `-on-resume` to
    complete. The job must stay on-hold, so it must not be updated.

    Must not block. The return value is ignored."))

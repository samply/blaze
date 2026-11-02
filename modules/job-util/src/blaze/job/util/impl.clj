(ns blaze.job.util.impl)

(defn retry-delay-micros
  "Returns the delay in microseconds before the retry with index `i` (starting
  at 0) of a job update that failed because of a concurrent update.

  The delay is an exponential backoff of 2^i milliseconds plus a jitter of up
  to another 2^i milliseconds, so that competing updates of the same job don't
  retry in lockstep. The `jitter` is a number in the range [0, 1) that is
  scaled to the jitter range."
  [i jitter]
  (let [base (* 1000 (bit-shift-left 1 i))]
    (+ base (long (* jitter base)))))

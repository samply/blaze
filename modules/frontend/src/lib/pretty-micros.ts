import prettyNum from '$lib/pretty-num';

/** Formats a latency given in microseconds, switching to milliseconds at 1000 µs. */
export default function prettyMicros(micros: number): string {
  return micros < 1000 ? `${prettyNum(micros)} µs` : `${prettyNum(micros / 1000)} ms`;
}

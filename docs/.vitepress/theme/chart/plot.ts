// Scale, tick and layout maths shared by the performance chart components.
//
// The charts keep the 640x480 aspect ratio of the gnuplot PNGs they replace, so
// their height is known from the width at first layout and nothing shifts while
// the page loads.
export const WIDTH = 640;
export const HEIGHT = 480;

/** Font size of tick and axis labels, in user units. */
export const FONT_SIZE = 12;

/** Font size of the chart title, in user units. */
export const TITLE_FONT_SIZE = 14;

// There is no text measurement at build time, so label widths are estimated
// from the character count. 0.55 em is the average advance of the site font at
// the sizes used here.
const AVERAGE_ADVANCE = 0.55;

/** Estimates the rendered width of `text` at `fontSize`. */
export function textWidth(text: string, fontSize = FONT_SIZE): number {
  return text.length * fontSize * AVERAGE_ADVANCE;
}

// Tick steps are restricted to the 1/2/5 family gnuplot also draws from.
const MANTISSAS = [1, 2, 5];

function steps(span: number): number[] {
  const exponent = Math.floor(Math.log10(span || 1));
  const result: number[] = [];
  for (let e = exponent - 2; e <= exponent + 1; e++) {
    for (const mantissa of MANTISSAS) {
      result.push(mantissa * Math.pow(10, e));
    }
  }
  return result.sort((a, b) => a - b);
}

// Floating point multiples of a step land next to the intended value rather
// than on it, which turns a tick of 0.30000000000000004 into a label nobody
// wants. Rounding to the step's own precision restores the exact value.
function round(value: number, step: number): number {
  const digits = Math.max(0, -Math.floor(Math.log10(step)) + 1);
  return parseFloat(value.toFixed(digits));
}

// A value a hair below a multiple of the step — 2199.9999999999995 for a step
// of 2200 — would otherwise drop that multiple's tick.
const EPSILON = 1e-9;

function ticksOf(min: number, max: number, step: number): number[] {
  const first = Math.ceil(min / step - EPSILON);
  const last = Math.floor(max / step + EPSILON);
  return Array.from({ length: Math.max(0, last - first + 1) }, (_, i) =>
    round((first + i) * step, step),
  );
}

export interface Axis {
  min: number;
  max: number;
  ticks: number[];
}

/**
 * Lays out a linear axis covering `[dataMin, dataMax]`.
 *
 * `min` and `max` pin the corresponding end of the axis; an end left `null` is
 * extended to the next tick beyond the data. `target` is the number of
 * intervals aimed for — the step whose interval count comes closest to it wins,
 * ties going to the denser one.
 */
export function axis(
  dataMin: number,
  dataMax: number,
  min: number | null,
  max: number | null,
  target: number,
): Axis {
  const lo = min ?? Math.min(dataMin, 0);
  const hi = Math.max(max ?? dataMax, lo + Number.MIN_VALUE);
  const from = (step: number) =>
    min ?? round(Math.floor(lo / step + EPSILON) * step, step);
  const to = (step: number) =>
    max ?? round(Math.ceil(hi / step - EPSILON) * step, step);
  const distance = (step: number) =>
    Math.abs((to(step) - from(step)) / step - target);
  const step = steps(hi - lo).reduce((best, step) =>
    distance(step) < distance(best) ? step : best,
  );
  return {
    min: from(step),
    max: to(step),
    ticks: ticksOf(from(step), to(step), step),
  };
}

/** Returns a log axis spanning `[min, max]` with an explicit list of ticks. */
export function logAxis(min: number, max: number, ticks: number[]): Axis {
  return { min, max, ticks };
}

/** Formats `value` as a tick label, appending `suffix` if given. */
export function tick(value: number, suffix = ""): string {
  const text =
    Number.isInteger(value) ? String(value) : String(parseFloat(value.toFixed(3)));
  return suffix ? `${text} ${suffix}` : text;
}

/** Returns a function projecting a value of `axis` onto `[from, to]`. */
export function scale(
  axis: Axis,
  from: number,
  to: number,
  log = false,
): (value: number) => number {
  if (log) {
    const min = Math.log(axis.min);
    const span = Math.log(axis.max) - min;
    return (value) => from + ((Math.log(value) - min) / span) * (to - from);
  }
  const span = axis.max - axis.min;
  return (value) => from + ((value - axis.min) / span) * (to - from);
}

/**
 * Returns the outline of a bar of width `width` rising from `baseline` to `top`.
 *
 * The end the data reaches is capped with a 4px radius while the baseline stays
 * square, so the bar reads as anchored to the axis.
 */
export function bar(
  x: number,
  width: number,
  baseline: number,
  top: number,
): string {
  const height = Math.abs(baseline - top);
  const radius = Math.min(4, width / 2, height);
  const direction = top < baseline ? 1 : -1;
  const end = top + direction * radius;
  return [
    `M${x} ${baseline}`,
    `L${x} ${end}`,
    `Q${x} ${top} ${x + radius} ${top}`,
    `L${x + width - radius} ${top}`,
    `Q${x + width} ${top} ${x + width} ${end}`,
    `L${x + width} ${baseline}`,
    "Z",
  ].join("");
}

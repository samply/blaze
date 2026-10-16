<script setup lang="ts">
// An animated AI chat in which an assistant with a shell tool answers
// questions about the patients on a Blaze server by writing a CQL library and
// a Measure YAML and evaluating them with blazectl.
//
// The animation starts once the chat scrolls into view. Readers who prefer
// reduced motion get the whole conversation at once.
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { type Step, conversation } from "./conversation";

interface Item {
  step: Step;
  /** The part of the text shown so far, for streamed assistant messages. */
  text: string;
  done: boolean;
}

type State = "idle" | "playing" | "paused" | "finished";

const TICK = 30;
const TYPE_DELAY = 32;
const WORD_DELAY = 45;

const items = ref<Item[]>([]);
const draft = ref("");
const state = ref<State>("idle");
const messages = ref<HTMLElement | null>(null);
const root = ref<HTMLElement | null>(null);

let run = 0;
let reducedMotion = false;
let observer: IntersectionObserver | null = null;

class Cancelled extends Error {}

/** Waits `ms` of unpaused time, rejecting once a newer run has started. */
function wait(ms: number, id: number): Promise<void> {
  return new Promise((resolve, reject) => {
    let left = ms;
    const tick = () => {
      if (id !== run) return reject(new Cancelled());
      if (state.value !== "paused") left -= TICK;
      if (left <= 0) resolve();
      else setTimeout(tick, TICK);
    };
    setTimeout(tick, TICK);
  });
}

function scrollToEnd() {
  nextTick(() => {
    const el = messages.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function push(step: Step, done: boolean): Item {
  const item = reactive({
    step,
    text: done && step.kind === "assistant" ? step.text : "",
    done,
  });
  items.value.push(item);
  scrollToEnd();
  return item;
}

async function playStep(step: Step, id: number) {
  switch (step.kind) {
    case "user":
      for (const char of step.text) {
        draft.value += char;
        await wait(TYPE_DELAY, id);
      }
      await wait(350, id);
      draft.value = "";
      push(step, true);
      await wait(700, id);
      break;
    case "assistant": {
      const item = push(step, false);
      for (const word of step.text.split(/(?<=\s)/)) {
        item.text += word;
        scrollToEnd();
        await wait(WORD_DELAY, id);
      }
      item.done = true;
      scrollToEnd();
      await wait(step.chart ? 2200 : 900, id);
      break;
    }
    case "file":
      push(step, true);
      await wait(1000, id);
      break;
    case "command": {
      const item = push(step, false);
      await wait(1600, id);
      item.done = true;
      scrollToEnd();
      await wait(1400, id);
      break;
    }
  }
}

function showAll() {
  run++;
  draft.value = "";
  items.value = [];
  for (const step of conversation) push(step, true);
  state.value = "finished";
}

async function play() {
  if (reducedMotion) return showAll();
  const id = ++run;
  items.value = [];
  draft.value = "";
  state.value = "playing";
  try {
    await wait(500, id);
    for (const step of conversation) await playStep(step, id);
    state.value = "finished";
  } catch (e) {
    if (!(e instanceof Cancelled)) throw e;
  }
}

function toggle() {
  if (state.value === "playing") state.value = "paused";
  else if (state.value === "paused") state.value = "playing";
  else play();
}

/** Splits a message into plain, bold (`**…**`) and code (`` `…` ``) runs. */
function segments(text: string) {
  return text
    .split(/(\*\*[^*]+\*\*|`[^`]+`)/)
    .filter((s) => s !== "")
    .map((s) =>
      s.startsWith("**") && s.endsWith("**") && s.length > 4
        ? { kind: "bold", text: s.slice(2, -2) }
        : s.startsWith("`") && s.endsWith("`") && s.length > 2
          ? { kind: "code", text: s.slice(1, -1) }
          : { kind: "plain", text: s },
    );
}

function maxValue(step: Step) {
  return step.kind === "assistant" && step.chart
    ? Math.max(...step.chart.map((b) => b.value))
    : 1;
}

onMounted(() => {
  reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reducedMotion) return showAll();
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting) && state.value === "idle") {
        observer?.disconnect();
        play();
      }
    },
    { threshold: 0.4 },
  );
  if (root.value) observer.observe(root.value);
});

onBeforeUnmount(() => {
  run++;
  observer?.disconnect();
});
</script>

<template>
  <figure
    ref="root"
    class="cql-chat"
    aria-label="Animated chat: an AI assistant answers questions about the patients on a Blaze server by writing CQL measures and evaluating them with blazectl."
  >
    <div class="cql-chat-window">
      <div class="cql-chat-header">
        <span class="cql-chat-title">Patient counts on Blaze</span>
        <span class="cql-chat-tools">
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <path d="M3 5l3 3-3 3M8 11h5" />
          </svg>
          Shell access
        </span>
      </div>

      <div ref="messages" class="cql-chat-messages">
        <template v-for="(item, index) in items" :key="index">
          <div v-if="item.step.kind === 'user'" class="cql-chat-user">
            {{ item.step.text }}
          </div>

          <div
            v-else-if="item.step.kind === 'assistant'"
            class="cql-chat-assistant"
          >
            <p>
              <template v-for="(s, i) in segments(item.text)" :key="i">
                <strong v-if="s.kind === 'bold'">{{ s.text }}</strong>
                <code v-else-if="s.kind === 'code'">{{ s.text }}</code>
                <template v-else>{{ s.text }}</template>
              </template>
            </p>
            <div
              v-if="item.done && item.step.chart"
              class="cql-chat-chart"
              role="table"
              aria-label="Patients by birth decade"
            >
              <div
                v-for="(bar, i) in item.step.chart"
                :key="bar.label"
                class="cql-chat-bar-row"
                role="row"
                :style="{ '--delay': `${i * 60}ms` }"
              >
                <span class="cql-chat-bar-label" role="cell">{{
                  bar.label
                }}</span>
                <span class="cql-chat-bar-track" role="cell">
                  <span
                    class="cql-chat-bar"
                    :style="{
                      width: `${(bar.value / maxValue(item.step)) * 100}%`,
                    }"
                  />
                </span>
                <span class="cql-chat-bar-value" role="cell">{{
                  bar.value
                }}</span>
              </div>
            </div>
          </div>

          <div v-else class="cql-chat-tool">
            <div class="cql-chat-tool-head">
              <svg
                v-if="item.step.kind === 'file'"
                viewBox="0 0 16 16"
                aria-hidden="true"
              >
                <path d="M4 1.5h5l3 3v10H4z M9 1.5v3h3" />
              </svg>
              <svg v-else viewBox="0 0 16 16" aria-hidden="true">
                <path d="M3 5l3 3-3 3M8 11h5" />
              </svg>
              <span v-if="item.step.kind === 'file'">
                Wrote <code>{{ item.step.name }}</code>
              </span>
              <span v-else>{{ item.done ? "Ran command" : "Running command" }}</span>
              <span
                class="cql-chat-status"
                :class="{ 'cql-chat-status-done': item.done }"
                :aria-label="item.done ? 'Done' : 'Running'"
              />
            </div>
            <pre v-if="item.step.kind === 'file'">{{ item.step.content }}</pre>
            <template v-else>
              <pre>{{ item.step.command }}</pre>
              <pre
                v-if="item.done"
                class="cql-chat-output"
              >{{ item.step.output }}</pre>
            </template>
          </div>
        </template>
      </div>

      <div class="cql-chat-composer" aria-hidden="true">
        <span class="cql-chat-input" :class="{ 'cql-chat-typing': draft }">{{
          draft || "Ask anything"
        }}</span>
        <span class="cql-chat-send" :class="{ 'cql-chat-send-ready': draft }">
          <svg viewBox="0 0 16 16"><path d="M8 13V3M3.5 7.5L8 3l4.5 4.5" /></svg>
        </span>
      </div>
    </div>

    <div class="cql-chat-controls">
      <button type="button" @click="toggle">
        {{
          state === "playing"
            ? "Pause"
            : state === "paused"
              ? "Resume"
              : state === "finished"
                ? "Replay"
                : "Play"
        }}
      </button>
      <button
        v-if="state === 'playing' || state === 'paused'"
        type="button"
        @click="play"
      >
        Restart
      </button>
    </div>
  </figure>
</template>

<style scoped>
.cql-chat {
  margin: 24px 0;
}

.cql-chat-window {
  display: flex;
  flex-direction: column;
  height: 560px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  background: var(--vp-c-bg);
  overflow: hidden;
}

.cql-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
  font-size: 14px;
}

.cql-chat-title {
  font-weight: 600;
  color: var(--vp-c-text-1);
}

.cql-chat-tools {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--vp-c-text-2);
  font-size: 13px;
}

svg {
  width: 16px;
  height: 16px;
  flex: none;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.cql-chat-messages {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 16px;
  overflow-y: auto;
  font-size: 15px;
  line-height: 1.6;
}

.cql-chat-messages > * {
  flex: none;
}

.cql-chat-user {
  align-self: flex-end;
  max-width: 80%;
  padding: 8px 14px;
  border-radius: 16px 16px 4px 16px;
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-text-1);
  overflow-wrap: anywhere;
}

.cql-chat-assistant {
  max-width: 92%;
  color: var(--vp-c-text-1);
}

.cql-chat-assistant p {
  margin: 0;
}

.cql-chat-assistant code,
.cql-chat-tool-head code {
  font-family: var(--vp-font-family-mono);
  font-size: 0.875em;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--vp-code-bg);
  color: var(--vp-code-color);
}

.cql-chat-tool {
  max-width: 92%;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
  overflow: hidden;
}

.cql-chat-tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  color: var(--vp-c-text-2);
  font-size: 13px;
}

.cql-chat-tool pre {
  margin: 0;
  padding: 10px 12px;
  border-top: 1px solid var(--vp-c-divider);
  background: var(--vp-code-block-bg);
  color: var(--vp-c-text-1);
  font-family: var(--vp-font-family-mono);
  font-size: 12.5px;
  line-height: 1.55;
  overflow-x: auto;
}

.cql-chat-tool pre.cql-chat-output {
  color: var(--vp-c-text-2);
}

.cql-chat-status {
  width: 12px;
  height: 12px;
  margin-left: auto;
  border: 2px solid var(--vp-c-divider);
  border-top-color: var(--vp-c-brand-1);
  border-radius: 50%;
  animation: cql-chat-spin 0.8s linear infinite;
}

.cql-chat-status-done {
  border: none;
  background: var(--vp-c-green-1);
  animation: none;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12'%3E%3Cpath d='M2 6.5l2.5 2.5L10 3.5' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E")
    center / contain no-repeat;
}

@keyframes cql-chat-spin {
  to {
    transform: rotate(360deg);
  }
}

.cql-chat-chart {
  display: grid;
  gap: 4px;
  margin-top: 12px;
  max-width: 420px;
  font-size: 13px;
}

.cql-chat-bar-row {
  display: grid;
  grid-template-columns: 48px 1fr 28px;
  align-items: center;
  gap: 10px;
}

.cql-chat-bar-label {
  color: var(--vp-c-text-2);
  font-variant-numeric: tabular-nums;
}

.cql-chat-bar-track {
  height: 14px;
}

.cql-chat-bar {
  display: block;
  height: 100%;
  border-radius: 0 3px 3px 0;
  background: var(--vp-c-brand-1);
  transform-origin: left;
  animation: cql-chat-grow 0.5s ease-out var(--delay) both;
}

.cql-chat-bar-value {
  color: var(--vp-c-text-1);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

@keyframes cql-chat-grow {
  from {
    transform: scaleX(0);
  }
}

.cql-chat-composer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 12px 12px;
  padding: 8px 8px 8px 16px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 20px;
  background: var(--vp-c-bg-soft);
  font-size: 15px;
}

.cql-chat-input {
  flex: 1;
  min-width: 0;
  color: var(--vp-c-text-3);
  overflow-wrap: anywhere;
}

.cql-chat-input.cql-chat-typing {
  color: var(--vp-c-text-1);
}

.cql-chat-input.cql-chat-typing::after {
  content: "";
  display: inline-block;
  width: 1px;
  height: 1.1em;
  margin-left: 1px;
  vertical-align: text-bottom;
  background: currentColor;
}

.cql-chat-send {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--vp-c-default-soft);
  color: var(--vp-c-text-3);
  transition: background-color 0.2s;
}

.cql-chat-send-ready {
  background: var(--vp-c-brand-1);
  color: var(--vp-c-white);
}

.cql-chat-controls {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.cql-chat-controls button {
  padding: 4px 14px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-1);
  font-size: 14px;
  font-weight: 500;
}

.cql-chat-controls button:hover {
  border-color: var(--vp-c-brand-1);
}

.cql-chat-controls button:focus-visible {
  outline: 2px solid var(--vp-c-brand-1);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .cql-chat-window {
    height: 500px;
  }

  .cql-chat-messages {
    padding: 16px 12px;
    font-size: 14px;
  }

  .cql-chat-user,
  .cql-chat-assistant,
  .cql-chat-tool {
    max-width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .cql-chat-bar,
  .cql-chat-status {
    animation: none;
  }
}
</style>

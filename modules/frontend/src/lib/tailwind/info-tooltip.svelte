<script lang="ts">
  import type { Snippet } from 'svelte';
  import { InformationCircle } from 'svelte-heros-v2';
  import { fade } from 'svelte/transition';

  interface Props {
    /** Accessible name of the trigger button. */
    label: string;
    /** Explanatory content shown in the tooltip. */
    children?: Snippet;
  }

  let { label, children }: Props = $props();

  let open = $state(false);

  const id = $props.id();
</script>

<svelte:window
  onkeydown={(event) => {
    if (open && event.key === 'Escape') open = false;
  }}
/>

<div class="relative inline-flex">
  <button
    type="button"
    aria-label={label}
    aria-expanded={open}
    aria-describedby={open ? id : undefined}
    onclick={() => (open = !open)}
    onmouseenter={() => (open = true)}
    onmouseleave={() => (open = false)}
    onfocus={() => (open = true)}
    onblur={() => (open = false)}
    class="cursor-pointer rounded-full text-gray-400 hover:text-gray-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:hover:text-gray-300"
  >
    <InformationCircle class="size-5" />
  </button>
  {#if open}
    <div
      {id}
      role="tooltip"
      transition:fade={{ duration: 100 }}
      class="absolute top-full right-0 z-10 mt-2 w-72 rounded-md bg-white px-3 py-2 text-left text-xs font-normal text-gray-700 shadow-lg ring-1 ring-black/5 dark:bg-gray-800 dark:text-gray-300"
    >
      {@render children?.()}
    </div>
  {/if}
</div>

<script lang="ts">
  import type { Snippet } from 'svelte';

  import { fade } from 'svelte/transition';

  interface Props {
    name: string;
    children?: Snippet;
  }

  let { name, children }: Props = $props();

  let open = $state(false);
</script>

<div class="relative inline-block text-left">
  <button
    type="button"
    class="inline-flex w-full justify-center gap-x-1.5 rounded-md bg-white px-2 py-1 text-sm font-semibold text-gray-900 ring-1 ring-gray-300 ring-inset hover:bg-gray-50 enabled:cursor-pointer"
    id="menu-button"
    onclick={() => (open = !open)}
    aria-expanded="true"
    aria-haspopup="true"
  >
    {name}
    <svg
      class="-mr-1 size-5 text-gray-400"
      viewBox="0 0 20 20"
      fill="currentColor"
      aria-hidden="true"
      data-slot="icon"
    >
      <path
        fill-rule="evenodd"
        d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
        clip-rule="evenodd"
      />
    </svg>
  </button>

  <!--
		Dropdown menu, show/hide based on menu state.

		Entering: "transition ease-out duration-100"
			From: "transform opacity-0 scale-95"
			To: "transform opacity-100 scale-100"
		Leaving: "transition ease-in duration-75"
			From: "transform opacity-100 scale-100"
			To: "transform opacity-0 scale-95"
	-->
  {#if open}
    <div
      class="absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-none"
      role="menu"
      aria-orientation="vertical"
      aria-labelledby="menu-button"
      tabindex="-1"
      transition:fade={{ duration: 100 }}
    >
      <div class="py-1" role="none">
        {@render children?.()}
      </div>
    </div>
  {/if}
</div>

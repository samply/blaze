<script lang="ts">
  import type { Snippet } from 'svelte';
  import { fade } from 'svelte/transition';
  import { ChevronDown } from 'svelte-heros-v2';

  interface Props {
    name: string;
    trigger?: Snippet<[func: () => void]>;
    children?: Snippet;
    open?: boolean;
  }

  let { name, children, trigger, open = $bindable(false) }: Props = $props();

  let toggle = () => (open = !open);
</script>

<div class="relative inline-block text-left">
  {#if trigger}
    {@render trigger(toggle)}
  {:else}
    <button
      type="button"
      class="inline-flex w-full justify-center gap-x-1.5 rounded-md bg-white dark:bg-gray-800 px-2 py-1 text-sm font-semibold text-gray-900 dark:text-gray-100 ring-1 ring-gray-300 dark:ring-gray-500 ring-inset hover:bg-gray-50 hover:dark:bg-gray-700 enabled:cursor-pointer"
      id="menu-button"
      onclick={toggle}
      aria-expanded="true"
      aria-haspopup="true"
    >
      {name}
      <ChevronDown variation="mini" class="-mr-1 size-5 text-gray-400" />
    </button>
  {/if}

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
      class="absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-md bg-white dark:bg-gray-800 shadow-lg ring-1 ring-black/5 focus:outline-none"
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

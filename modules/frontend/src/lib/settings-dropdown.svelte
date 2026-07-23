<script lang="ts">
  import { Cog6Tooth } from 'svelte-heros-v2';
  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import SummaryToggle from '$lib/summary-toggle.svelte';

  interface Props {
    /** Whether summary mode is currently enabled for this view. */
    enabled: boolean;
    /** Resource type the choice applies to; omitted for system-wide views. */
    type?: string;
  }

  let { enabled, type }: Props = $props();
</script>

<Dropdown name="settings">
  {#snippet trigger(toggle)}
    <button
      type="button"
      onclick={toggle}
      class="flex-none inline-flex items-center rounded-md bg-white dark:bg-gray-800 px-3 py-2 text-gray-500 dark:text-gray-400 ring-1 ring-inset ring-gray-300 dark:ring-gray-500 hover:bg-gray-50 hover:dark:bg-gray-700 enabled:cursor-pointer"
      aria-haspopup="true"
    >
      <span class="sr-only">Settings</span>
      <Cog6Tooth class="size-6" />
    </button>
  {/snippet}
  <div class="p-1">
    <SummaryToggle {enabled} {type} />
  </div>
</Dropdown>

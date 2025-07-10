<script lang="ts">
  import { createEventDispatcher } from 'svelte';

  const dispatch = createEventDispatcher();

  function click() {
    dispatch('click');
  }
</script>

<button
  type="button"
  class="rounded-md bg-white p-2 text-sm font-semibold text-gray-900 disabled:opacity-50 ring-1 ring-inset ring-gray-300 enabled:hover:bg-gray-50"
  aria-label="move search param up"
  onclick={click}
>
  <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
    <path
      fill-rule="evenodd"
      d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z"
      clip-rule="evenodd"
    />
  </svg>
</button>

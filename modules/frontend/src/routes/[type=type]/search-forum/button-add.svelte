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
  aria-label="add new search param below"
  onclick={click}
>
  <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
    <path
      d="M10.75 4.75a.75.75 0 00-1.5 0v4.5h-4.5a.75.75 0 000 1.5h4.5v4.5a.75.75 0 001.5 0v-4.5h4.5a.75.75 0 000-1.5h-4.5v-4.5z"
    />
  </svg>
</button>

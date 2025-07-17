<script lang="ts">
  import { createEventDispatcher } from 'svelte';

  interface Props {
    disabled?: boolean;
  }

  let { disabled = false }: Props = $props();

  const dispatch = createEventDispatcher();

  function click() {
    dispatch('click');
  }
</script>

<button
  type="button"
  {disabled}
  class="rounded-md bg-white p-2 text-sm font-semibold text-gray-900 disabled:opacity-50 ring-1 ring-inset ring-gray-300 enabled:hover:bg-gray-50 enabled:cursor-pointer"
  aria-label="move search param up"
  onclick={click}
>
  <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
    <path
      fill-rule="evenodd"
      d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z"
      clip-rule="evenodd"
    />
  </svg>
</button>

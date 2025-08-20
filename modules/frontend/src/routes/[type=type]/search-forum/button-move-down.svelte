<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { ArrowDown } from 'svelte-heros-v2';

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
  <ArrowDown variation="mini" class="size-5" />
</button>

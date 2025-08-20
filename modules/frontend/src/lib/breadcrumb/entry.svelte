<script lang="ts">
  import type { Snippet } from 'svelte';
  import { ChevronRight } from 'svelte-heros-v2';

  interface Props {
    children?: Snippet;
  }

  let { children }: Props = $props();
</script>

<li>
  <div class="flex items-center">
    <ChevronRight variation="mini" class="size-5 shrink-0 text-gray-400" />
    {@render children?.()}
  </div>
</li>

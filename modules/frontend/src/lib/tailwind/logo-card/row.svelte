<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title: string;
    children?: Snippet;
  }

  let { title, children }: Props = $props();
</script>

<div class="flex justify-between gap-x-4 py-3">
  <dt class="text-gray-500">{title}</dt>
  <dd class="text-gray-700">
    {@render children?.()}
  </dd>
</div>

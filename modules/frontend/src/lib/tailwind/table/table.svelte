<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    clazz?: string;
    caption?: Snippet;
    head?: Snippet;
    children?: Snippet;
  }

  let { clazz = '', caption, head, children }: Props = $props();
</script>

<div class={clazz}>
  {@render caption?.()}
  <div class="-mx-4 mt-4 sm:-mx-0">
    <table class="min-w-full divide-y divide-gray-300">
      <thead>
        {@render head?.()}
      </thead>
      <tbody class="divide-y divide-gray-200 bg-white">
        {@render children?.()}
      </tbody>
    </table>
  </div>
</div>

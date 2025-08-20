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
    <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-500">
      <thead>
        {@render head?.()}
      </thead>
      <tbody class="divide-y divide-gray-200 dark:divide-gray-600 bg-white dark:bg-gray-800">
        {@render children?.()}
      </tbody>
    </table>
  </div>
</div>

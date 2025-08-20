<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title?: Snippet;
    description?: Snippet;
    children?: Snippet;
  }

  let { title, description, children }: Props = $props();
</script>

<div>
  <div class="px-4 sm:px-0">
    <h3 class="text-base font-semibold leading-7 text-gray-900 dark:text-gray-100">
      {@render title?.()}
    </h3>
    <p class="mt-1 max-w-2xl text-sm leading-6 text-gray-500 dark:text-gray-400">
      {@render description?.()}
    </p>
  </div>
  <div class="mt-6 border-t border-gray-100 dark:border-gray-700">
    <dl class="divide-y divide-gray-100 dark:divide-gray-700" role="list">
      {@render children?.()}
    </dl>
  </div>
</div>

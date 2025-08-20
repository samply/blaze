<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    name: string;
    children?: Snippet;
  }

  let { name, children }: Props = $props();
</script>

<fieldset>
  <legend class="sr-only">{name}</legend>
  <div class="sm:grid sm:grid-cols-3 sm:gap-4 sm:py-6">
    <div class="text-sm/6 font-semibold text-gray-900 dark:text-gray-100" aria-hidden="true">
      {name}
    </div>
    <div class="mt-4 sm:col-span-2 sm:mt-0">
      <div class="max-w-lg space-y-6">
        {@render children?.()}
      </div>
    </div>
  </div>
</fieldset>

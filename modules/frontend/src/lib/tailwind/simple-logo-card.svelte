<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { ResolvedPathname } from '$app/types';

  interface Props {
    href: ResolvedPathname;
    title: string;
    children?: Snippet;
  }

  let { href, title, children }: Props = $props();
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -->

<li class="overflow-hidden rounded-xl border border-gray-200 dark:border-gray-600">
  <div class="relative flex items-center gap-x-4 bg-gray-50 p-6 dark:bg-gray-700">
    <div
      class="h-12 w-12 flex-none rounded-lg bg-white object-cover p-2.5 ring-1 ring-gray-900/10 dark:bg-gray-800"
    >
      {@render children?.()}
    </div>
    <div class="text-sm leading-6 font-medium text-gray-900 dark:text-gray-100">
      <a {href}>
        <span class="absolute inset-x-0 -top-px bottom-0"></span>
        {title}
      </a>
    </div>
  </div>
</li>

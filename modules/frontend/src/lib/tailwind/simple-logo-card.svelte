<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    href: string;
    title: string;
    children?: Snippet;
  }

  let { href, title, children }: Props = $props();
</script>

<li class="overflow-hidden rounded-xl border border-gray-200">
  <div class="relative flex items-center gap-x-4 bg-gray-50 p-6">
    <div class="h-12 w-12 flex-none p-2.5 rounded-lg bg-white object-cover ring-1 ring-gray-900/10">
      {@render children?.()}
    </div>
    <div class="text-sm font-medium leading-6 text-gray-900">
      <a {href}>
        <span class="absolute inset-x-0 -top-px bottom-0"></span>
        {title}
      </a>
    </div>
  </div>
</li>

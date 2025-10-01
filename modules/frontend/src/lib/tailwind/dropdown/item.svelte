<script lang="ts">
  import type { ResolvedPathname } from '$app/types';

  interface Props {
    name: string;
    url: ResolvedPathname;
  }

  let { name, url }: Props = $props();
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -->

<!-- Active: "bg-gray-100 text-gray-900 dark:text-gray-100 outline-none", Not Active: "text-gray-700 dark:text-gray-300" -->
<a
  href={url}
  class="block px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 hover:dark:bg-gray-600 hover:text-gray-900 hhover:dark:text-gray-100"
  role="menuitem"
  tabindex="-1"
  id="menu-item-0">{name}</a
>

<script lang="ts">
  import type { Snippet } from 'svelte';

  import { ArrowTopRightOnSquare } from 'svelte-heros-v2';

  interface Props {
    href: string;
    title?: string | undefined;
    children?: Snippet;
  }

  let { href, title = undefined, children }: Props = $props();
</script>

<a
  {href}
  {title}
  rel="external"
  class="font-medium text-indigo-600 dark:text-indigo-300 hover:text-indigo-500 hover:dark:text-indigo-400"
  >{@render children?.()}
  <ArrowTopRightOnSquare
    variation="mini"
    class="inline align-text-bottom w-4 h-4 text-gray-500 dark:text-gray-400"
  /></a
>

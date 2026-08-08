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
  class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-300 hover:dark:text-indigo-400"
  >{@render children?.()}
  <ArrowTopRightOnSquare
    variation="mini"
    class="inline h-4 w-4 align-text-bottom text-gray-500 dark:text-gray-400"
  /></a
>

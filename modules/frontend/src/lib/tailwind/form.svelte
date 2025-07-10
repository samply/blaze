<script lang="ts">
  import type { Snippet } from 'svelte';

  import { enhance } from '$app/forms';

  interface Props {
    class?: string;
    children?: Snippet;
    buttons?: Snippet;
  }

  let { class: cssClass, children, buttons }: Props = $props();
</script>

<form class={cssClass} method="POST" use:enhance>
  <div class="space-y-12 sm:space-y-16">
    {@render children?.()}
  </div>

  <div class="mt-6 flex items-center justify-end gap-x-6">
    {@render buttons?.()}
  </div>
</form>

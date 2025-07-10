<script lang="ts">
  import { base } from '$app/paths';

  interface Props {
    active?: boolean;
    id: string;
    label: string;
  }

  let { active = false, id, label }: Props = $props();
</script>

{#if active}
  <a
    href="{base}{id}"
    class="border-indigo-500 text-gray-900 inline-flex items-center border-b-2 px-1 pt-1 text-sm font-medium"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href="{base}{id}"
    class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 inline-flex items-center border-b-2 px-1 pt-1 text-sm font-medium"
    >{label}</a
  >
{/if}

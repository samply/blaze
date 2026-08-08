<script lang="ts">
  import { resolve } from '$app/paths';

  import Entry from './entry.svelte';

  interface Props {
    type: string;
    id: string;
    vid: string;
  }

  let { type, id, vid }: Props = $props();
</script>

<Entry>
  <a
    href={resolve('/[type=type]/[id=id]/_history/[vid=vid]', { type: type, id: id, vid: vid })}
    class="ml-4 text-sm font-medium text-gray-500 hover:text-gray-700 dark:text-gray-400 hover:dark:text-gray-300"
    >Version-{vid}</a
  >
</Entry>

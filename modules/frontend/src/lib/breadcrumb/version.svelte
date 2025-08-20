<script lang="ts">
  import { resolve } from '$app/paths';

  import Entry from './entry.svelte';

  interface Props {
    type: string;
    id: string;
    vid: string;
  }

  let props: Props = $props();
</script>

<Entry>
  <a
    href={resolve('/[type=type]/[id=id]/_history/[vid=vid]', props)}
    class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400 hover:text-gray-700 hover:dark:text-gray-300"
    >Version-{props.vid}</a
  >
</Entry>

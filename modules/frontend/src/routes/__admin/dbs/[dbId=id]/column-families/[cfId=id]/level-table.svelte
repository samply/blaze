<script lang="ts">
  import Table from '$lib/tailwind/table/table.svelte';
  import LevelRow from './level-row.svelte';
  import type { Level } from './+page.server.js';

  interface Props {
    levels: Level[];
  }

  let { levels }: Props = $props();
</script>

<Table clazz="mt-4">
  {#snippet caption()}
    <h4 class="text-base leading-6 font-semibold text-gray-900 dark:text-gray-100">Levels</h4>
  {/snippet}
  {#snippet head()}
    <tr>
      <th
        scope="col"
        class="py-3.5 pr-3 pl-4 text-left text-sm font-semibold text-gray-900 sm:pl-0 dark:text-gray-100"
        >Level</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        ># Files</th
      >
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >Size</th
      >
    </tr>
  {/snippet}

  {#each levels as level (level.level)}
    <LevelRow {...level} />
  {/each}
</Table>

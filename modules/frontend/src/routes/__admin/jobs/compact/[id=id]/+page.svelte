<script lang="ts">
  import type { PageProps } from './$types';

  import { pascalCase } from 'change-case';
  import { invalidateAll } from '$app/navigation';
  import Status from '$lib/jobs/status.svelte';
  import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
  import DateTime from '$lib/values/date-time.svelte';
  import Row from '$lib/tailwind/description/left-aligned/row-3-2.svelte';
  import humanizeDuration from 'humanize-duration';
  import { resolve } from '$app/paths';

  let { data }: PageProps = $props();

  // reload page data every 5 seconds if the job is still in progress
  $effect(() => {
    if (data.job.status === 'ready' || data.job.status === 'in-progress') {
      const timeout = setTimeout(() => {
        invalidateAll();
      }, 5000);

      return () => {
        clearTimeout(timeout);
      };
    }
  });
</script>

<svelte:head>
  <title>Job #{data.job.number} - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <DescriptionList>
    {#snippet title()}
      Job #{data.job.number}
    {/snippet}
    {#snippet description()}
      Last Updated
      <DateTime value={data.job.lastUpdated} />
    {/snippet}
    <Row title="Status">
      <Status job={data.job} />
    </Row>
    <Row title="Type">Compact a Database Column Family</Row>
    <Row title="Created">
      <DateTime value={data.job.authoredOn} />
    </Row>
    <Row title="Database">
      <a
        class="text-indigo-600 hover:text-indigo-900"
        href={resolve('/__admin/dbs/[dbId=id]', { dbId: data.job.database })}
        >{pascalCase(data.job.database)}</a
      >
    </Row>
    <Row title="Column Family">
      <a
        class="text-indigo-600 hover:text-indigo-900"
        href={resolve('/__admin/dbs/[dbId=id]/column-families/[cfId=id]', {
          dbId: data.job.database,
          cfId: data.job.columnFamily
        })}>{pascalCase(data.job.columnFamily)}</a
      >
    </Row>
    {#if data.job.error}
      <Row title="Error">
        {data.job.error}
      </Row>
    {/if}
    {#if data.job.processingDuration}
      <Row title="Processing Duration">
        {humanizeDuration(data.job.processingDuration * 1000, { round: true })}
      </Row>
    {/if}
  </DescriptionList>
</main>

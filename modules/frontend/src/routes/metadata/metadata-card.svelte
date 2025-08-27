<script lang="ts">
  import type { CapabilityStatement } from 'fhir/r5';
  import { RestfulInteraction } from '$lib/fhir.js';
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import { isTabActive } from '$lib/util.js';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';

  import DateTime from '$lib/values/date-time.svelte';
  import { Check, ArrowDownTray, XMark } from 'svelte-heros-v2';
  import Object from '$lib/resource/json/object.svelte';
  import TabItem from '$lib/tab-item.svelte';
  import InteractionTh from './interaction-th.svelte';
  import InteractionTd from './interaction-td.svelte';
  import Table from '$lib/tailwind/table/table.svelte';

  interface Props {
    capabilityStatement: CapabilityStatement;
    capabilityStatementObject: FhirObject;
  }

  let { capabilityStatement, capabilityStatementObject }: Props = $props();
</script>

<div class="overflow-hidden">
  <div class="border-b border-gray-200 dark:border-gray-600">
    <nav class="-mb-px flex space-x-8 sm:px-6" aria-label="Tabs">
      <TabItem name="default" label="Interactions" />
      <TabItem name="json" label="Json" />
    </nav>
  </div>
  {#if isTabActive(page.url, 'default')}
    <Table clazz="mt-4 sm:px-6">
      {#snippet caption()}
        <div>
          <h1 class="text-base font-semibold leading-6 text-gray-900 dark:text-gray-100">
            {capabilityStatement.software?.name} v{capabilityStatement.software?.version}
          </h1>
          <p class="mt-2 text-sm text-gray-700 dark:text-gray-300">
            Last Updated&nbsp;<DateTime value={capabilityStatement.date} />
          </p>
        </div>
      {/snippet}

      {#snippet head()}
        <tr>
          <th
            scope="col"
            class="whitespace-nowrap py-3.5 pl-4 align-bottom text-left text-sm font-semibold text-gray-900 dark:text-gray-100 sm:pl-0"
            >Resource Type</th
          >
          <InteractionTh label="Profile" />
          <InteractionTh label="Read" />
          <InteractionTh label="VRead" />
          <InteractionTh label="Search-Type" />
          <InteractionTh label="History" />
          <InteractionTh label="History-Type" />
          <InteractionTh label="Create" />
          <InteractionTh label="Update" />
          <InteractionTh label="Patch" />
          <InteractionTh label="Delete" />
          <InteractionTh label="Read History" />
          <InteractionTh label="Update Create" />
          <InteractionTh label="Conditional Create" />
          <InteractionTh label="Conditional Read" />
          <InteractionTh label="Conditional Update" />
          <InteractionTh label="Conditional Delete" />
        </tr>
      {/snippet}

      {#each capabilityStatement.rest?.at(0)?.resource || [] as resource (resource.type)}
        <tr>
          <td class="whitespace-nowrap py-2 pl-4 text-sm sm:pl-0 text-gray-900 dark:text-gray-100"
            ><a
              href={resolve('/metadata/[type=type]', { type: resource.type })}
              class="hover:text-gray-500 dark:text-gray-400">{resource.type}</a
            ></td
          >
          <td class="py-2 text-sm text-gray-900 dark:text-gray-100">
            <a
              href="{resolve('/StructureDefinition')}?url={resource.profile}&_format=json"
              download="{resource.type}.json"><ArrowDownTray class="mx-auto w-5 h-5" /></a
            >
          </td>
          <InteractionTd {resource} interaction={RestfulInteraction.read} />
          <InteractionTd {resource} interaction={RestfulInteraction.vread} />
          <InteractionTd {resource} interaction={RestfulInteraction.searchType} />
          <InteractionTd {resource} interaction={RestfulInteraction.historyInstance} />
          <InteractionTd {resource} interaction={RestfulInteraction.historyType} />
          <InteractionTd {resource} interaction={RestfulInteraction.create} />
          <InteractionTd {resource} interaction={RestfulInteraction.update} />
          <InteractionTd {resource} interaction={RestfulInteraction.patch} />
          <InteractionTd {resource} interaction={RestfulInteraction.delete} />
          <td class="py-2">
            {#if resource.readHistory}
              <Check class="mx-auto w-5 h-5 text-green-600 dark:text-green-400" />
            {:else}
              <XMark class="mx-auto w-5 h-5 text-red-600 dark:text-red-400" />
            {/if}
          </td>
          <td class="py-2">
            {#if resource.updateCreate}
              <Check class="mx-auto w-5 h-5 text-green-600 dark:text-green-400" />
            {:else}
              <XMark class="mx-auto w-5 h-5 text-red-600 dark:text-red-400" />
            {/if}
          </td>
          <td class="py-2">
            {#if resource.conditionalCreate}
              <Check class="mx-auto w-5 h-5 text-green-600 dark:text-green-400" />
            {:else}
              <XMark class="mx-auto w-5 h-5 text-red-600 dark:text-red-400" />
            {/if}
          </td>
          <td class="py-2">
            {#if resource.conditionalRead}
              <Check class="mx-auto w-5 h-5 text-green-600 dark:text-green-400" />
            {:else}
              <XMark class="mx-auto w-5 h-5 text-red-600 dark:text-red-400" />
            {/if}
          </td>
          <td class="py-2">
            {#if resource.conditionalUpdate}
              <Check class="mx-auto w-5 h-5 text-green-600 dark:text-green-400" />
            {:else}
              <XMark class="mx-auto w-5 h-5 text-red-600 dark:text-red-400" />
            {/if}
          </td>
          <td class="py-2">
            {#if resource.conditionalDelete !== 'not-supported'}
              <span title={resource.conditionalDelete}
                ><Check class="mx-auto size-5 text-green-600 dark:text-green-400" /></span
              >
            {:else}
              <XMark class="mx-auto w-5 h-5 text-red-600 dark:text-red-400" />
            {/if}
          </td>
        </tr>
      {/each}
    </Table>
  {:else}
    <pre class="flex overflow-auto text-sm"><code class="p-4"
        ><Object object={capabilityStatementObject} /></code
      ></pre>
  {/if}
</div>

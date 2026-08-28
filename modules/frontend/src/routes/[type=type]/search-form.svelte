<script lang="ts">
  import { preventDefault } from 'svelte/legacy';

  import type { SearchMetadata } from '$lib/search-metadata.js';
  import { initQueryParams, selectParam, submitParams } from './query-param.js';
  import {
    insertAtIndex,
    moveDownAtIndex,
    moveUpAtIndex,
    removeAtIndex,
    updateAtIndex
  } from '$lib/util.js';
  import { afterNavigate, goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';

  import CheckboxActive from './search-forum/checkbox-active.svelte';
  import SearchParamComboBox from './search-forum/search-param-combo-box.svelte';
  import QueryParamValue from './search-forum/query-param-value.svelte';
  import ValueComboBox from './search-forum/value-combo-box.svelte';
  import ButtonMoveDown from './search-forum/button-move-down.svelte';
  import ButtonMoveUp from './search-forum/button-move-up.svelte';
  import RemoveButton from './search-forum/button-remove.svelte';
  import AddButton from './search-forum/button-add.svelte';

  import { fade } from 'svelte/transition';
  import { quintIn } from 'svelte/easing';
  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import Toggle from '$lib/tailwind/toggle.svelte';

  interface Props {
    searchMetadata: SearchMetadata;
    type: string;
  }

  let { searchMetadata, type }: Props = $props();

  // Filter out _summary from the dropdown options — it is managed by the
  // Result control, not the form's param rows.
  let filteredSearchParams = $derived(
    searchMetadata.searchParams.filter((p) => p.name !== '_summary')
  );

  let { queryParams: initialQueryParams, queryPlan: initialQueryPlan } = initQueryParams(
    page.url.searchParams
  );
  let queryParamsState = $state(initialQueryParams);
  let queryPlanState = $state(initialQueryPlan);

  afterNavigate((nav) => {
    if (nav.to) {
      const init = initQueryParams(nav.to.url.searchParams);
      queryParamsState = init.queryParams;
      queryPlanState = init.queryPlan;
    }
  });

  function send() {
    const params = submitParams(
      queryParamsState,
      queryPlanState,
      page.url.searchParams.get('_summary')
    );

    // eslint-disable-next-line svelte/no-navigation-without-resolve
    goto(`${resolve('/[type=type]', { type: type })}?${params}`);
  }

  let openSearchSettings = $state(false);
</script>

<form
  class="flex gap-2 border-b border-gray-200 px-4 py-5 sm:px-6 dark:border-gray-600"
  onsubmit={preventDefault(send)}
>
  <div class="flex grow flex-col gap-2">
    {#each queryParamsState as queryParam, index (queryParam.id)}
      <div in:fade={{ duration: 200, easing: quintIn }} class="flex gap-2">
        <CheckboxActive
          {index}
          active={queryParam.active}
          on:change={() =>
            (queryParamsState = updateAtIndex(queryParamsState, index, (p) => ({
              ...p,
              active: !p.active
            })))}
        />

        <SearchParamComboBox
          searchParams={filteredSearchParams}
          {index}
          bind:selected={queryParam.name}
        />
        {#if queryParam.name === '_include'}
          <ValueComboBox
            options={searchMetadata.searchIncludes}
            {index}
            bind:selected={queryParam.value}
          />
        {:else if queryParam.name === '_revinclude'}
          <ValueComboBox
            options={searchMetadata.searchRevIncludes}
            {index}
            bind:selected={queryParam.value}
          />
        {:else}
          <QueryParamValue {index} bind:value={queryParam.value} />
        {/if}
        {#if index === 0}
          <ButtonMoveDown
            disabled={queryParamsState.length < 2}
            on:click={() => (queryParamsState = moveDownAtIndex(queryParamsState, index))}
          />
        {:else}
          <ButtonMoveUp
            on:click={() => (queryParamsState = moveUpAtIndex(queryParamsState, index))}
          />
        {/if}
        <RemoveButton
          disabled={queryParamsState.length === 1}
          on:click={() =>
            (queryParamsState = removeAtIndex(queryParamsState, index, selectParam(0)))}
        />
        <AddButton
          on:click={() =>
            (queryParamsState = insertAtIndex(
              queryParamsState,
              index,
              selectParam(Math.max(...queryParamsState.map((p) => p.id)) + 1)
            ))}
        />
      </div>
    {/each}
  </div>
  <div class="inline-flex rounded-md shadow-xs">
    <Dropdown name="search-settings" bind:open={openSearchSettings}>
      {#snippet trigger(toggle)}
        <div class="inline-flex rounded-md">
          <button
            type="submit"
            onclick={() => (openSearchSettings = false)}
            class="w-20 rounded-l-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 enabled:cursor-pointer"
            >Search
          </button>
          <button
            type="button"
            class="relative inline-flex items-center rounded-r-md border-l-1 border-indigo-800 bg-indigo-600 px-2 py-2 text-white hover:bg-indigo-500 focus-visible:outline-indigo-600 enabled:cursor-pointer"
            onclick={toggle}
          >
            <span class="sr-only">Search Options</span>
            <svg
              viewBox="0 0 20 20"
              fill="currentColor"
              data-slot="icon"
              aria-hidden="true"
              class="size-5"
            >
              <path
                d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
                clip-rule="evenodd"
                fill-rule="evenodd"
              />
            </svg>
          </button>
        </div>
      {/snippet}
      <div class="flex flex-col gap-3 p-1">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">Query options</h3>
        <Toggle
          id="query-plan"
          label="Show Plan"
          description="Show how the server executes the query."
          bind:checked={queryPlanState}
        />
      </div>
    </Dropdown>
  </div>
</form>

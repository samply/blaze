<script lang="ts">
  import { preventDefault } from 'svelte/legacy';

  import type { CapabilityStatementRestResourceSearchParam } from 'fhir/r4';
  import { SearchParamType } from '$lib/fhir.js';
  import type { QueryParam } from './query-param.js';
  import {
    defaultCount,
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
  import { error, type NumericRange } from '@sveltejs/kit';
  import Dropdown from '$lib/tailwind/dropdown.svelte';

  interface Props {
    searchParams: CapabilityStatementRestResourceSearchParam[];
    type: string;
  }

  let { searchParams, type }: Props = $props();
  let queryPlan = $state(false);

  async function loadSearchIncludes(type: string): Promise<string[]> {
    const res = await fetch(resolve('/[type=type]/__search-rev-includes', { type: type }), {
      headers: { Accept: 'application/json' }
    });

    if (!res.ok) {
      error(res.status as NumericRange<400, 599>, 'error while fetching the search includes');
    }

    return (await res.json()).searchIncludes;
  }

  async function loadSearchRevIncludes(type: string): Promise<string[]> {
    const res = await fetch(resolve('/[type=type]/__search-rev-includes', { type: type }), {
      headers: { Accept: 'application/json' }
    });

    if (!res.ok) {
      error(
        res.status as NumericRange<400, 599>,
        'error while fetching the search reverse includes'
      );
    }

    return (await res.json()).searchRevIncludes;
  }

  function removeInactiveModifier(name: string): [string, boolean] {
    const active = !name.endsWith(':inactive');
    return [active ? name : name.substring(0, name.length - 9), active];
  }

  function selectParam(id: number) {
    return {
      id: id,
      active: true,
      name: '__select',
      type: SearchParamType.special,
      value: ''
    };
  }

  function initQueryParams(urlSearchParams: URLSearchParams): QueryParam[] {
    const queryParams: QueryParam[] = [];
    for (const [name, value] of urlSearchParams) {
      if (name == '__explain') {
        queryPlan = value == 'true';
        continue;
      }
      if (name.startsWith('__')) {
        continue;
      }
      if (name == '_count' && value == defaultCount) {
        continue;
      }
      const [paramName, active] = removeInactiveModifier(name);
      queryParams.push({
        id: queryParams.length,
        active: active,
        name: paramName,
        type: SearchParamType.composite,
        value: value
      });
    }
    if (queryParams.length == 0) {
      queryParams.push(selectParam(queryParams.length));
    }
    return queryParams;
  }

  let queryParams = $state(initQueryParams(page.url.searchParams));

  afterNavigate((nav) => {
    if (nav.to) {
      queryParams = initQueryParams(nav.to.url.searchParams);
    }
  });

  function send() {
    const params = queryParams
      .filter((p) => p.name != '__select')
      .map((p) => ({ ...p, value: p.value.trim() }))
      .filter((p) => p.value.length != 0)
      .map((p) => [p.active ? p.name : p.name + ':inactive', p.value]) as string[][];
    if (queryPlan) params.push(['__explain', 'true']);
    goto(`${resolve('/[type=type]', { type: type })}/?${new URLSearchParams(params)}`);
  }

  let openSearchSettings = $state(false);
</script>

<form
  class="flex gap-2 px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-600"
  onsubmit={preventDefault(send)}
>
  <div class="grow flex flex-col gap-2">
    {#each queryParams as queryParam, index (queryParam.id)}
      <div in:fade={{ duration: 200, easing: quintIn }} class="flex gap-2">
        <CheckboxActive
          {index}
          active={queryParam.active}
          on:change={() =>
            (queryParams = updateAtIndex(queryParams, index, (p) => ({
              ...p,
              active: !p.active
            })))}
        />

        <SearchParamComboBox {searchParams} {index} bind:selected={queryParam.name} />
        {#if queryParam.name === '_include'}
          {#await loadSearchIncludes(type)}
            <ValueComboBox {index} bind:selected={queryParam.value} />
          {:then searchIncludes}
            <ValueComboBox options={searchIncludes} {index} bind:selected={queryParam.value} />
          {/await}
        {:else if queryParam.name === '_revinclude'}
          {#await loadSearchRevIncludes(type)}
            <ValueComboBox {index} bind:selected={queryParam.value} />
          {:then searchRevIncludes}
            <ValueComboBox options={searchRevIncludes} {index} bind:selected={queryParam.value} />
          {/await}
        {:else}
          <QueryParamValue {index} bind:value={queryParam.value} />
        {/if}
        {#if index === 0}
          <ButtonMoveDown
            disabled={queryParams.length < 2}
            on:click={() => (queryParams = moveDownAtIndex(queryParams, index))}
          />
        {:else}
          <ButtonMoveUp on:click={() => (queryParams = moveUpAtIndex(queryParams, index))} />
        {/if}
        <RemoveButton
          disabled={queryParams.length === 1}
          on:click={() => (queryParams = removeAtIndex(queryParams, index, selectParam(0)))}
        />
        <AddButton
          on:click={() =>
            (queryParams = insertAtIndex(
              queryParams,
              index,
              selectParam(Math.max(...queryParams.map((p) => p.id)) + 1)
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
            class="relative inline-flex items-center rounded-r-md bg-indigo-600 px-2 py-2 text-white border-l-1 border-indigo-800 hover:bg-indigo-500 focus-visible:outline-indigo-600 enabled:cursor-pointer"
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
      <div class="p-1">
        <div class="flex items-center gap-3">
          <div
            class="group relative inline-flex w-11 shrink-0 rounded-full bg-gray-200 p-0.5 inset-ring inset-ring-gray-900/5 outline-offset-2 outline-indigo-600 transition-colors duration-200 ease-in-out has-checked:bg-indigo-600 has-focus-visible:outline-2"
          >
            <span
              class="size-5 rounded-full bg-white dark:bg-gray-800 shadow-xs ring-1 ring-gray-900/5 transition-transform duration-200 ease-in-out group-has-checked:translate-x-5"
            ></span>
            <input
              id="query-plan"
              type="checkbox"
              name="query-plan"
              aria-labelledby="query-plan-label"
              class="absolute inset-0 appearance-none focus:outline-hidden"
              bind:checked={queryPlan}
            />
          </div>
          <div class="text-sm">
            <label
              id="query-plan-label"
              class="font-medium text-gray-900 dark:text-gray-100"
              for="query-plan">Show Plan</label
            >
          </div>
        </div>
      </div>
    </Dropdown>
  </div>
</form>

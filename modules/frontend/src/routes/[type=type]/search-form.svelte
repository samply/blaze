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
  import { base } from '$app/paths';
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

  interface Props {
    searchParams: CapabilityStatementRestResourceSearchParam[];
  }

  let { searchParams }: Props = $props();

  async function loadSearchIncludes(type: string): Promise<string[]> {
    const res = await fetch(`${base}/${type}/__search-includes`, {
      headers: { Accept: 'application/json' }
    });

    if (!res.ok) {
      error(res.status as NumericRange<400, 599>, 'error while fetching the search includes');
    }

    return (await res.json()).searchIncludes;
  }

  async function loadSearchRevIncludes(type: string): Promise<string[]> {
    const res = await fetch(`${base}/${type}/__search-rev-includes`, {
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

  function initQueryParams(
    searchParams: CapabilityStatementRestResourceSearchParam[],
    urlSearchParams: URLSearchParams
  ): QueryParam[] {
    const queryParams: QueryParam[] = [];
    for (const [name, value] of urlSearchParams) {
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

  let queryParams = $state(initQueryParams(searchParams, page.url.searchParams));

  afterNavigate((nav) => {
    if (nav.to) {
      queryParams = initQueryParams(searchParams, nav.to.url.searchParams);
    }
  });

  function send() {
    const params = queryParams
      .filter((p) => p.name != '__select')
      .map((p) => ({ ...p, value: p.value.trim() }))
      .filter((p) => p.value.length != 0)
      .map((p) => [p.active ? p.name : p.name + ':inactive', p.value]) as string[][];
    goto(`${base}/${page.params.type}/?${new URLSearchParams(params)}`);
  }
</script>

<form class="flex gap-2 px-4 py-5 sm:px-6 border-b border-gray-200" onsubmit={preventDefault(send)}>
  <div class="flex-grow flex flex-col gap-2">
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
          {#await loadSearchIncludes(page.params.type)}
            <ValueComboBox {index} bind:selected={queryParam.value} />
          {:then searchIncludes}
            <ValueComboBox options={searchIncludes} {index} bind:selected={queryParam.value} />
          {/await}
        {:else if queryParam.name === '_revinclude'}
          {#await loadSearchRevIncludes(page.params.type)}
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
  <div>
    <button
      type="submit"
      class="w-20 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
      >Search
    </button>
  </div>
</form>

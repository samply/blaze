<script lang="ts">
	import {
		type CapabilityStatement,
		type CapabilityStatementSearchParam,
		SearchParamType
	} from '../../fhir.js';
	import type { QueryParam } from './query-param.js';
	import { sortByProperty } from '../../util.js';
	import { defaultCount } from '../../util.js';
	import { goto } from '$app/navigation';
	import { base } from '$app/paths';
	import { page } from '$app/stores';
	import { afterNavigate } from '$app/navigation';

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

	import {
		removeAtIndex,
		insertAtIndex,
		updateAtIndex,
		moveUpAtIndex,
		moveDownAtIndex
	} from '../../util.js';

	export let capabilityStatement: CapabilityStatement;

	$: server = capabilityStatement.rest[0];
	$: resource = server.resource.find((r) => r.type == $page.params.type);
	$: searchParams = (
		resource?.searchParam === undefined
			? server.searchParam
			: [...server.searchParam, ...resource.searchParam]
	).sort(sortByProperty('name'));

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
		searchParams: CapabilityStatementSearchParam[],
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

	let queryParams = initQueryParams(searchParams, $page.url.searchParams);

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
		goto(`${base}/${$page.params.type}/?${new URLSearchParams(params)}`);
	}
</script>

{#if resource}
	<form
		class="flex gap-2 bg-white shadow sm:rounded-lg px-4 py-5 sm:px-6"
		on:submit|preventDefault={send}
	>
		<div class="flex-grow flex flex-col gap-2">
			{#each queryParams as queryParam, index (queryParam.id)}
				<div in:fade={{ duration: 300, easing: quintIn }} class="flex gap-2">
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
					{#if queryParam.name == '_include' && resource.searchInclude}
						<ValueComboBox
							options={resource.searchInclude}
							{index}
							bind:selected={queryParam.value}
						/>
					{:else if queryParam.name == '_revinclude' && resource.searchRevInclude}
						<ValueComboBox
							options={resource.searchRevInclude}
							{index}
							bind:selected={queryParam.value}
						/>
					{:else}
						<QueryParamValue {index} bind:value={queryParam.value} />
					{/if}
					{#if index == 0}
						<ButtonMoveDown
							disabled={queryParams.length < 2}
							on:click={() => (queryParams = moveDownAtIndex(queryParams, index))}
						/>
					{:else}
						<ButtonMoveUp on:click={() => (queryParams = moveUpAtIndex(queryParams, index))} />
					{/if}
					<RemoveButton
						disabled={queryParams.length == 1}
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
				class="w-20 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
				>Search</button
			>
		</div>
	</form>
{/if}

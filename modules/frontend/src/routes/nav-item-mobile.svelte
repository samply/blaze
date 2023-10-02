<script lang="ts">
	import { base } from '$app/paths';

	export let active = false;
	export let id: string;
	export let label: string;
</script>

{#if active}
	<a
		href="{base}{id}"
		class="border-indigo-500 bg-indigo-50 text-indigo-700 block border-l-4 py-2 pl-3 pr-4 text-base font-medium"
		aria-current="page">{label}</a
	>
{:else}
	<a
		href="{base}{id}"
		class="border-transparent text-gray-600 hover:border-gray-300 hover:bg-gray-50 hover:text-gray-800 block border-l-4 py-2 pl-3 pr-4 text-base font-medium"
		>{label}</a
	>
{/if}

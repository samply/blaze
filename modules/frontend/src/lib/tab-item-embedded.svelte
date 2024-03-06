<script lang="ts">
	export let name: string;
	export let label: string;
	export let selected: string;
</script>

{#if name == selected}
	<button
		on:click={() => {
			selected = name;
		}}
		class="border-indigo-500 text-indigo-600 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
		aria-current="page">{label}</button
	>
{:else}
	<button
		on:click={() => {
			selected = name;
		}}
		class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
		>{label}</button
	>
{/if}

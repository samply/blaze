<script lang="ts">
	export let name: string;
	export let toggle: string;
	export let enabled: boolean;
</script>

<tr>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{name}</td>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{toggle}</td>
	<td
		class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0"
		class:font-medium={enabled}
		class:text-gray-900={enabled}>{enabled}</td
	>
</tr>

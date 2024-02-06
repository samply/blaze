<script lang="ts">
	export let name: string;
	export let value: string | number;
	export let defaultValue: string | number;
</script>

<tr>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{name}</td>
	<td
		class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0"
		class:font-medium={value != defaultValue}
		class:text-gray-900={value != defaultValue}>{value}</td
	>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{defaultValue}</td>
</tr>

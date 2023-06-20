<script lang="ts">
	import { base } from '$app/paths';

	export let name: string;
	export let total: number;
</script>

<tr>
	<td class="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0"
		><a href="{base}/{name}">{name}</a></td
	>
	<td class="whitespace-nowrap px-3 py-4 text-sm text-gray-900">{total}</td>
</tr>

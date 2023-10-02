<script lang="ts">
	import prettyBytes from 'pretty-bytes';

	export let t: number;
	export let patientCount: number;
	export let memSize: number;
	export let exprForm: string;
</script>

<tr>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-right text-sm text-gray-500 sm:pl-0">{t}</td>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-right text-sm text-gray-500 sm:pl-0"
		>{patientCount}</td
	>
	<td class="whitespace-nowrap py-2 pl-4 pr-3 text-right text-sm text-gray-500 sm:pl-0"
		>{prettyBytes(memSize, { binary: true, maximumFractionDigits: 1 })}</td
	>
	<td class="py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{exprForm}</td>
</tr>

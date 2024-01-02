<script lang="ts">
	import { HttpVerb } from '../fhir.js';

	export let method: HttpVerb;

	function modeBgColor(mode: HttpVerb): string {
		switch (mode) {
			case HttpVerb.POST:
				return 'bg-green-100';
			case HttpVerb.PUT:
				return 'bg-fuchsia-100';
			default:
				return 'bg-red-100';
		}
	}

	function modeTextColor(mode: HttpVerb): string {
		switch (mode) {
			case HttpVerb.POST:
				return 'text-green-800';
			case HttpVerb.PUT:
				return 'text-fuchsia-800';
			default:
				return 'text-red-800';
		}
	}
</script>

<div class="ml-2 flex flex-shrink-0">
	<p
		class="inline-flex items-center rounded-full {modeBgColor(
			method
		)} px-2.5 py-0.5 text-xs font-medium {modeTextColor(method)}"
	>
		{method}
	</p>
</div>

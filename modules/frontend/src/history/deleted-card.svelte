<script lang="ts">
	import type { code, uri, instant } from '../fhir';
	import { HttpVerb } from '../fhir';
	import DateTime from '../values/date-time.svelte';
	import MethodBadge from './method-badge.svelte';

	export let request: {
		method: code;
		url: uri;
	};
	export let lastModified: instant;
</script>

<div class="overflow-hidden bg-white shadow sm:rounded-lg">
	<div class="px-4 py-5 sm:px-6">
		<div class="flex">
			<h3 class="flex-grow text-base font-semibold leading-6 text-gray-900">
				{request.url}
			</h3>
			<MethodBadge method={HttpVerb.DELETE} />
		</div>
		<p class="mt-1 max-w-2xl text-sm text-gray-500">
			Last Updated&nbsp;<DateTime value={lastModified} />
		</p>
	</div>
</div>

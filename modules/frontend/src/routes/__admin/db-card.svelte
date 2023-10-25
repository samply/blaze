<script lang="ts">
	import { base } from '$app/paths';

	export let id: string;
	export let name: string;
</script>

<li
	class="relative flex items-center overflow-hidden px-6 py-5 border border-gray-300 bg-white shadow-sm sm:rounded-lg focus-within:ring-2 focus-within:ring-indigo-500 focus-within:ring-offset-2 hover:border-gray-400"
>
	<div class="flex-shrink-0 mr-1">
		<svg
			xmlns="http://www.w3.org/2000/svg"
			fill="none"
			viewBox="0 0 24 24"
			stroke-width="1.5"
			stroke="currentColor"
			class="w-6 h-6"
		>
			<path
				stroke-linecap="round"
				stroke-linejoin="round"
				d="M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375m16.5 0v3.75m-16.5-3.75v3.75m16.5 0v3.75C20.25 16.153 16.556 18 12 18s-8.25-1.847-8.25-4.125v-3.75m16.5 0c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125"
			/>
		</svg>
	</div>
	<div class="min-w-0 flex-1">
		<a href="{base}/__admin/db/{id}" class="focus:outline-none">
			<span class="absolute inset-0" aria-hidden="true"></span>
			<h3 class="text-base font-semibold leading-6 text-gray-900">{name}</h3>
		</a>
	</div>
</li>

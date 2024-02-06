<script lang="ts">
	export let href: string;
	export let title: string;
</script>

<li class="overflow-hidden rounded-xl border border-gray-200">
	<div class="flex items-center gap-x-4 border-b border-gray-900/5 bg-gray-50 p-6">
		<div class="h-12 w-12 flex-none p-2.5 rounded-lg bg-white object-cover ring-1 ring-gray-900/10">
			<a {href}>
				<slot name="logo" />
			</a>
		</div>
		<div class="text-sm font-medium leading-6 text-gray-900">
			<a {href}>{title}</a>
		</div>
	</div>
	<dl class="-my-3 divide-y divide-gray-100 px-6 py-4 text-sm leading-6">
		<slot />
	</dl>
</li>

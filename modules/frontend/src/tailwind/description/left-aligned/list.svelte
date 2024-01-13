<script lang="ts">
	export let clazz: string;
</script>

<div class={clazz}>
	<div class="px-4 sm:px-0">
		<h3 class="text-base font-semibold leading-7 text-gray-900">
			<slot name="title" />
		</h3>
		<p class="mt-1 max-w-2xl text-sm leading-6 text-gray-500">
			<slot name="description" />
		</p>
	</div>
	<div class="mt-6 border-t border-gray-100">
		<dl class="divide-y divide-gray-100">
			<slot />
		</dl>
	</div>
</div>

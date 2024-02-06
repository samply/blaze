<script lang="ts">
	export let clazz: string = '';
</script>

<div class={clazz}>
	<slot name="caption" />
	<div class="-mx-4 mt-4 sm:-mx-0">
		<table class="min-w-full divide-y divide-gray-300">
			<thead>
				<slot name="head" />
			</thead>
			<tbody class="divide-y divide-gray-200 bg-white">
				<slot />
			</tbody>
		</table>
	</div>
</div>

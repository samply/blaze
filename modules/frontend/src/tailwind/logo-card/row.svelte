<script lang="ts">
	export let title: string;
</script>

<div class="flex justify-between gap-x-4 py-3">
	<dt class="text-gray-500">{title}</dt>
	<dd class="text-gray-700">
		<slot />
	</dd>
</div>

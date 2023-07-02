<script lang="ts">
	export let duration: number;
</script>

<span
	class="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800}"
	>Time: {duration < 1000
		? duration + ' ms'
		: (duration / 1000).toLocaleString(undefined, {
				minimumFractionDigits: 1,
				maximumFractionDigits: 1
		  }) + ' s'}</span
>

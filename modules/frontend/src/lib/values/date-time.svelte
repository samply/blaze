<script lang="ts">
	export let value: string;
</script>

<time datetime={value}
	>{new Date(value).toLocaleString(undefined, {
		dateStyle: 'medium',
		timeStyle: 'long'
	})}</time
>

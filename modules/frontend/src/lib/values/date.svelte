<script lang="ts">
	export let value: string;
</script>

<time datetime={value}
	>{new Date(value).toLocaleDateString(undefined, {
		dateStyle: 'medium'
	})}</time
>

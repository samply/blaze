<script lang="ts">
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    total: number;
  }

  let { total }: Props = $props();
</script>

<Badge
  color="green"
  title="Total number of entries is {Number(total).toLocaleString()}."
  value="Total: {Number(total).toLocaleString()}"
/>

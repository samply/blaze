<script lang="ts">
  interface Props {
    total: number;
  }

  let { total }: Props = $props();
</script>

<span
  role="note"
  title="Total number of entries is {Number(total).toLocaleString()}."
  class="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800}"
  >Total: {Number(total).toLocaleString()}</span
>

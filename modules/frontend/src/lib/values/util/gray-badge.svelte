<script lang="ts">
  interface Props {
    value: string;
    title?: string;
  }

  let { value, title }: Props = $props();
</script>

<span
  role="note"
  {title}
  class="rounded-full ml-1 bg-gray-100 px-2 py-1 text-xs font-medium text-gray-800">{value}</span
>

<script lang="ts">
  interface Props {
    label: string;
  }

  let { label }: Props = $props();
</script>

<th
  scope="col"
  class="whitespace-nowrap px-2 py-3.5 align-bottom text-sm font-semibold text-gray-900"
  ><span class="wm-v-rl rotate-180">{label}</span></th
>

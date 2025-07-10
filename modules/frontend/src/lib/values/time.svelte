<script lang="ts">
  interface Props {
    value: string;
  }

  let { value }: Props = $props();
</script>

<time datetime={value}>{value}</time>

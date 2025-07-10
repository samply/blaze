<script lang="ts">
  interface Props {
    value: string;
  }

  let { value }: Props = $props();
</script>

<time datetime={value}
  >{new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'long'
  })}</time
>

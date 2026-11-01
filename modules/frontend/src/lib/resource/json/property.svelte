<script lang="ts">
  import Value from './value.svelte';

  interface Props {
    indent: number;
    isLast: boolean;
    name: string;
    value: unknown;
  }

  let { indent, isLast, name, value }: Props = $props();
</script>

{' '.repeat(indent)}<span class="text-orange-700 dark:text-orange-200">{JSON.stringify(name)}</span
>{': '}<Value {indent} {value} />{isLast ? '\n' : ',\n'}

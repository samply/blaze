<script module lang="ts">
  export type Color = 'gray' | 'green' | 'fuchsia' | 'red' | 'orange';
</script>

<script lang="ts">
  interface Props {
    value: string;
    title?: string;
    color?: Color;
  }

  function chooseColor(color?: Color): string {
    switch (color) {
      case 'green':
        return 'bg-green-100 text-green-800';
      case 'fuchsia':
        return 'bg-fuchsia-100 text-fuchsia-800';
      case 'red':
        return 'bg-red-100 text-red-800';
      case 'orange':
        return 'bg-orange-100 text-orange-800';
      default:
        return 'bg-gray-200 text-gray-800';
    }
  }

  let { value, title, color }: Props = $props();
</script>

<span
  role="note"
  {title}
  class="rounded-full ml-1 {chooseColor(color)} px-2 py-1 text-xs font-medium"
>
  {value}
</span>

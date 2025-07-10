<script lang="ts">
  interface Props {
    name: string;
    url: string;
  }

  let { name, url }: Props = $props();
</script>

<!-- Active: "bg-gray-100 text-gray-900 outline-none", Not Active: "text-gray-700" -->
<a
  href={url}
  class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 hover:text-gray-900"
  role="menuitem"
  tabindex="-1"
  id="menu-item-0">{name}</a
>

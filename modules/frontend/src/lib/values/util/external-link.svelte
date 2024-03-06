<script lang="ts">
	import ExternLinkIcon from './extern-link-icon.svelte';

	export let href: string;
	export let title: string | undefined = undefined;
</script>

<a {href} {title} class="font-medium text-indigo-600 hover:text-indigo-500"
	><slot /> <ExternLinkIcon /></a
>

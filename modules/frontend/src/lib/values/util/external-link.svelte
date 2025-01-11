<script lang="ts">
	import ExternLinkIcon from './extern-link-icon.svelte';

	interface Props {
		href: string;
		title?: string | undefined;
		children?: import('svelte').Snippet;
	}

	let { href, title = undefined, children }: Props = $props();
</script>

<a {href} {title} class="font-medium text-indigo-600 hover:text-indigo-500"
	>{@render children?.()} <ExternLinkIcon /></a
>

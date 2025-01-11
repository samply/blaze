<script lang="ts">
	import type { FhirObject } from '../resource-card.js';
	import Property from './property.svelte';

	interface Props {
		indent?: number;
		insideArray?: boolean;
		object: FhirObject;
	}

	let { indent = 0, insideArray = false, object }: Props = $props();
</script>

{insideArray ? ' '.repeat(indent) : ''}<span>{'{\n'}</span
>{#each object.properties as property, index (property.name)}<Property
		indent={indent + 4}
		isLast={index + 1 === object.properties.length}
		{property}
	/>{/each}<span>{' '.repeat(indent)}{'}'}</span>

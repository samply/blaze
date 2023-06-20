<script lang="ts">
	import type { FhirObject } from '../resource-card';
	import Property from './property.svelte';

	export let indent = 0;
	export let insideArray = false;
	export let object: FhirObject;
</script>

{insideArray ? ' '.repeat(indent) : ''}<span>{'{\n'}</span
>{#each object.properties as property, index (property.name)}<Property
		indent={indent + 4}
		isLast={index + 1 == object.properties.length}
		{property}
	/>{/each}<span>{' '.repeat(indent)}{'}'}</span>

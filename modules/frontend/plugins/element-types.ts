import type {
  Bundle,
  ElementDefinition,
  ElementDefinitionType,
  StructureDefinition
} from 'fhir/r4';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Plugin } from 'vite';
import type { ElementType, ElementTypes } from '../src/lib/element-types.js';
import { toTitleCase } from '../src/lib/util.js';

/**
 * The path of the FHIR definitions of the fhir-structure module, resolved
 * relative to this file and so independent of the working directory.
 */
export const profilesPath = fileURLToPath(
  new URL(
    '../../fhir-structure/target/generated-resources/blaze/fhir/4.0.1/profiles-resources.json',
    import.meta.url
  )
);

const fhirTypeUrl = 'http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type';

function isResourceDefinition(resource: unknown): resource is StructureDefinition {
  const sd = resource as StructureDefinition;
  return (
    sd.resourceType === 'StructureDefinition' &&
    sd.kind === 'resource' &&
    !sd.abstract &&
    sd.derivation === 'specialization'
  );
}

/**
 * The FHIR type code of `type`. Elements like `id` have a FHIRPath system type
 * as code and carry their FHIR type in an extension.
 */
function typeCode(type: ElementDefinitionType): string {
  return type.extension?.find((e) => e.url === fhirTypeUrl)?.valueUrl ?? type.code;
}

function toElementType(type: ElementDefinitionType): ElementType {
  const code = typeCode(type);
  return code === 'canonical' && type.targetProfile !== undefined
    ? { code, targetProfile: type.targetProfile }
    : { code };
}

function addElement(types: Record<string, ElementType>, element: ElementDefinition) {
  const name = element.path.split('.')[1];
  const elementTypes = element.type ?? [];

  if (name.endsWith('[x]')) {
    const choiceName = name.substring(0, name.length - 3);
    for (const type of elementTypes) {
      const t = toElementType(type);
      types[choiceName + toTitleCase(t.code)] = { ...t, choiceName };
    }
  } else if (elementTypes.length > 0) {
    types[name] = toElementType(elementTypes[0]);
  } else if (element.contentReference !== undefined) {
    // an element defined by reference to another element, which is always a
    // backbone element, like ClaimResponse.adjudication
    types[name] = { code: 'BackboneElement' };
  }
}

function resourceElementTypes(sd: StructureDefinition): Record<string, ElementType> {
  const types: Record<string, ElementType> = {};
  for (const element of sd.snapshot?.element ?? []) {
    if (element.path.split('.').length === 2) {
      addElement(types, element);
    }
  }
  return types;
}

/**
 * Generates the element types of the top-level elements of all resource types
 * defined in `bundle`, a bundle of StructureDefinitions like
 * `profiles-resources.json` of the FHIR definitions.
 */
export function generateElementTypes(bundle: Bundle): ElementTypes {
  const table: ElementTypes = {};
  for (const entry of bundle.entry ?? []) {
    if (isResourceDefinition(entry.resource)) {
      table[entry.resource.type] = resourceElementTypes(entry.resource);
    }
  }
  return table;
}

/**
 * Provides the module `virtual:element-types`, the element types generated
 * from the StructureDefinitions of the fhir-structure module.
 */
export function elementTypes(): Plugin {
  const id = 'virtual:element-types';
  const resolvedId = '\0' + id;

  return {
    name: 'element-types',
    resolveId(source) {
      return source === id ? resolvedId : undefined;
    },
    load(loadId) {
      if (loadId !== resolvedId) {
        return undefined;
      }
      if (!existsSync(profilesPath)) {
        this.error(`Missing ${profilesPath}. Please run \`make build-fhir-structure\` first.`);
      }
      this.addWatchFile(profilesPath);
      const table = generateElementTypes(JSON.parse(readFileSync(profilesPath, 'utf8')));
      return `export default ${JSON.stringify(table)};`;
    }
  };
}

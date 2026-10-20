import { describe, expect, it } from 'vitest'
import { SUPPORTED_LANGUAGES } from './languages'

type Tree = { [key: string]: string | Tree }

// Every namespace, bundled or lazy-loaded.
const files = import.meta.glob<Tree>('./locales/*/*.json', { eager: true, import: 'default' })

function bundle(language: string, namespace: string): Tree {
  const tree = files[`./locales/${language}/${namespace}.json`]
  if (!tree) {
    throw new Error(`Missing translations: ${language}/${namespace}`)
  }
  return tree
}

function flatten(tree: Tree, prefix = ''): Record<string, string> {
  return Object.entries(tree).reduce<Record<string, string>>((result, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return typeof value === 'string' ? { ...result, [path]: value } : { ...result, ...flatten(value, path) }
  }, {})
}

function interpolationNames(value: string): string[] {
  return [...value.matchAll(/{{\s*(\w+)\s*}}/g)].map((match) => match[1] ?? '').sort()
}

const namespaces = [...new Set(Object.keys(files).map((path) => path.split('/')[3]!.replace('.json', '')))].sort()

it('provides every namespace in every supported language', () => {
  for (const language of SUPPORTED_LANGUAGES) {
    for (const namespace of namespaces) {
      expect(() => bundle(language, namespace)).not.toThrow()
    }
  }
})

describe.each(namespaces)('translation namespace "%s"', (namespace) => {
  const english = flatten(bundle('en', namespace))
  const french = flatten(bundle('fr', namespace))

  it('has exactly the same keys in French and English', () => {
    expect(Object.keys(french).sort()).toEqual(Object.keys(english).sort())
  })

  it('uses the same interpolation values in both languages', () => {
    for (const [key, value] of Object.entries(english)) {
      expect(interpolationNames(french[key] ?? ''), key).toEqual(interpolationNames(value))
    }
  })

  it('follows French typography, so punctuation never wraps onto its own line', () => {
    const misplaced = Object.entries(french)
      .filter(([, value]) => /'| [?!;:]|« | »/.test(value))
      .map(([key]) => key)
    expect(misplaced).toEqual([])
  })

  it('has no empty translations', () => {
    const empty = [...Object.entries(english), ...Object.entries(french)]
      .filter(([, value]) => value.trim() === '')
      .map(([key]) => key)
    expect(empty).toEqual([])
  })
})

import { describe, expect, it } from 'vitest'
import tokens from './tokens.css?raw'

/** WCAG 2.2 AA for the semantic tokens in both themes (design-system.md §3). */
function theme(selector: string): Record<string, string> {
  const start = tokens.indexOf(selector)
  const body = tokens.slice(tokens.indexOf('{', start) + 1, tokens.indexOf('}', start))
  return Object.fromEntries([...body.matchAll(/--color-([\w-]+):\s*(#[0-9a-f]{6})/gi)].map((match) => [match[1]!, match[2]!]))
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5]
    .map((index) => parseInt(hex.slice(index, index + 2), 16) / 255)
    .map((channel) => (channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}

function contrast(foreground: string, background: string): number {
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (lighter! + 0.05) / (darker! + 0.05)
}

const SURFACES = ['canvas', 'surface', 'surface-raised', 'surface-sunken']
const TEXT = ['text', 'text-muted', 'text-subtle', 'accent', 'success', 'warning', 'danger', 'info']
const ON_FILL = [
  ['accent-contrast', 'accent'],
  ['accent-contrast', 'accent-hover'],
  ['danger-contrast', 'danger'],
  ['accent', 'accent-soft'],
  ['success', 'success-soft'],
  ['warning', 'warning-soft'],
  ['danger', 'danger-soft'],
  ['info', 'info-soft'],
]
const GRAPHICS = ['border-control', 'focus-ring', ...[
  'scheduled', 'confirmed', 'waiting', 'in-consultation', 'completed', 'cancelled', 'no-show',
].map((status) => `status-${status}`)]

describe.each(['light', 'dark'])('%s theme', (name) => {
  const colors = theme(`[data-theme='${name}']`)

  it('defines every audited token', () => {
    const audited = [...SURFACES, ...TEXT, ...ON_FILL.flat(), ...GRAPHICS]
    expect(audited.filter((token) => !colors[token])).toEqual([])
  })

  const failures = (pairs: string[][], minimum: number) =>
    pairs
      .map(([foreground, background]) => [foreground, background, contrast(colors[foreground!]!, colors[background!]!)] as const)
      .filter(([, , ratio]) => ratio < minimum)
      .map(([foreground, background, ratio]) => `${foreground} on ${background}: ${ratio.toFixed(2)}`)

  it('keeps text readable on every surface (4.5:1)', () => {
    expect(failures(TEXT.flatMap((text) => SURFACES.map((surface) => [text, surface])), 4.5)).toEqual([])
  })

  it('keeps text readable on filled and tinted backgrounds (4.5:1)', () => {
    expect(failures(ON_FILL, 4.5)).toEqual([])
  })

  it('keeps control outlines, focus rings, and status marks visible (3:1)', () => {
    expect(failures(GRAPHICS.flatMap((graphic) => SURFACES.map((surface) => [graphic, surface])), 3)).toEqual([])
  })
})

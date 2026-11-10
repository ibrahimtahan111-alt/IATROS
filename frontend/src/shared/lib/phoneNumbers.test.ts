import { describe, expect, it } from 'vitest'
import { formatPhone, parsePhone, phoneRegionOf, phoneRegionOfLocale } from './phoneNumbers'

describe('phone numbers', () => {
  it('splits a stored number into its country and national number', () => {
    expect(parsePhone('+30 690 000 0003')).toEqual({ region: 'GR', number: '690 000 0003' })
    expect(parsePhone('+33612345678')).toEqual({ region: 'FR', number: '612345678' })
    expect(parsePhone('+358 40 123 4567')).toEqual({ region: 'FI', number: '40 123 4567' })
  })

  it('attributes a shared calling code to its main country', () => {
    expect(parsePhone('+1 212 555 0100').region).toBe('US')
    expect(parsePhone('+44 20 7946 0000').region).toBe('GB')
    expect(parsePhone('+7 495 000 0000').region).toBe('RU')
  })

  it('keeps numbers without a known calling code whole', () => {
    expect(parsePhone('210 000 0000')).toEqual({ region: undefined, number: '210 000 0000' })
    expect(parsePhone('+999 123')).toEqual({ region: undefined, number: '+999 123' })
    expect(parsePhone('')).toEqual({ region: undefined, number: '' })
  })

  it('stores nothing for a calling code without a number', () => {
    expect(formatPhone('GR', '690 000 0003')).toBe('+30 690 000 0003')
    expect(formatPhone('CA', '416 555 0100')).toBe('+1 416 555 0100')
    expect(formatPhone(undefined, '690 000 0003')).toBe('690 000 0003')
    expect(formatPhone('GR', '  ')).toBe('')
  })

  it('finds a default country from a phone number or a language tag', () => {
    expect(phoneRegionOf('+30 210 000 0000')).toBe('GR')
    expect(phoneRegionOf(null)).toBeUndefined()
    expect(phoneRegionOfLocale('fr')).toBe('FR')
    expect(phoneRegionOfLocale('en-GB')).toBe('GB')
    expect(phoneRegionOfLocale('not a tag')).toBeUndefined()
  })
})

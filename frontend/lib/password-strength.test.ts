import { describe, expect, it } from 'vitest'
import { MIN_PASSWORD_LENGTH, scorePassword } from './password-strength'

describe('scorePassword', () => {
  it('is idle with guidance when nothing is typed', () => {
    const result = scorePassword('')
    expect(result.score).toBe(0)
    expect(result.hint).toContain(String(MIN_PASSWORD_LENGTH))
  })

  it('counts down the characters still needed below the minimum', () => {
    expect(scorePassword('abcde')).toEqual({ score: 1, hint: '3 more characters needed' })
    expect(scorePassword('abcdefg')).toEqual({ score: 1, hint: '1 more character needed' })
  })

  it('scores a minimum-length lowercase password as weak', () => {
    expect(scorePassword('abcdefgh').score).toBe(1)
  })

  it('rewards length, mixed case with digits, and symbols', () => {
    expect(scorePassword('abcdefghijkl').score).toBe(2)
    expect(scorePassword('Abcdefg1').score).toBe(2)
    expect(scorePassword('Abcdefgh1jkl').score).toBe(3)
    expect(scorePassword('Abcdefgh1jkl!')).toEqual({ score: 4, hint: 'Strong' })
  })

  it('never exceeds 4', () => {
    expect(scorePassword('Aa1!Aa1!Aa1!Aa1!Aa1!').score).toBe(4)
  })
})

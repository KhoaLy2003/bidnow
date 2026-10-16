import { describe, expect, it } from 'vitest'
import { DEVELOPERS, PRINCIPLES, STACK, githubUrl, initials } from './about'

describe('initials', () => {
  it('uses the first letter of each word', () => {
    expect(initials('Hiep Nguyen')).toBe('HN')
  })

  it('caps at two letters', () => {
    expect(initials('Ly Ngoc Dang Khoa')).toBe('LN')
  })

  it('upper-cases a lower-case name', () => {
    expect(initials('khoa ly')).toBe('KL')
  })

  it('copes with extra whitespace and an empty name', () => {
    expect(initials('  Hiep   Nguyen ')).toBe('HN')
    expect(initials('')).toBe('')
  })
})

describe('githubUrl', () => {
  it('builds a profile link from a handle', () => {
    expect(githubUrl('KhoaLy2003')).toBe('https://github.com/KhoaLy2003')
  })
})

describe('about content', () => {
  it('lists every developer with a handle that is safe to put in a URL', () => {
    expect(DEVELOPERS.length).toBeGreaterThan(0)
    for (const { handle } of DEVELOPERS) expect(handle).toMatch(/^[A-Za-z0-9-]+$/)
  })

  it('numbers the principles in order', () => {
    expect(PRINCIPLES.map((p) => p.n)).toEqual(PRINCIPLES.map((_, i) => `0${i + 1}`))
  })

  it('gives every tech-stack group at least one item, with unique group titles', () => {
    const titles = STACK.map((g) => g.title)
    expect(new Set(titles).size).toBe(titles.length)
    for (const group of STACK) expect(group.items.length).toBeGreaterThan(0)
  })
})

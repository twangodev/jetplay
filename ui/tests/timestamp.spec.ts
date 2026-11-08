import { test, expect } from '@playwright/test'
import { parseTimestamp, formatTimestamp } from '../src/lib/timestamp'

test('parses seconds and precise colon timestamps', () => {
  for (const [input, expected] of [
    ['0', 0], ['12.4', 12.4], ['12.45', 12.45], ['12.450', 12.45],
    ['  1:23.450  ', 83.45], ['90:00', 5400], ['00:00.001', 0.001],
    ['1:02:03.004', 3723.004], ['00:59:59.999', 3599.999], ['000:01', 1],
  ] as const) {
    expect(parseTimestamp(input), input).toBe(expected)
  }
})

test('rejects malformed, negative, and ambiguous timestamps', () => {
  for (const input of [
    '', ' ', '-1', '+1', '1e3', 'NaN', 'Infinity', '.5', '1.', '1.2345',
    '1:2', '1:60', '1:02:3', '1:60:00', '1:02:60', '1:02:03:04',
    '1:02.1234', '1 :02', '1: 02', '12seconds', '1,250',
  ]) {
    expect(parseTimestamp(input), input).toBeNull()
  }
})

test('rejects overflowing values while retaining safe millisecond precision', () => {
  expect(parseTimestamp('9007199254740.991')).toBe(Number.MAX_SAFE_INTEGER / 1000)
  for (const input of ['9007199254740.992', '9007199254741', '9999999999999999999999:00', '9'.repeat(400)]) {
    expect(parseTimestamp(input), input).toBeNull()
  }
})

test('formats precision and carries rounded milliseconds across minute and hour boundaries', () => {
  for (const [seconds, expected] of [
    [0, '00:00.000'], [0.001, '00:00.001'], [12.45, '00:12.450'],
    [83.45, '01:23.450'], [59.9996, '01:00.000'],
    [3599.9996, '1:00:00.000'], [3723.004, '1:02:03.004'], [360000, '100:00:00.000'],
  ] as const) {
    expect(formatTimestamp(seconds), String(seconds)).toBe(expected)
  }
})

test('formats invalid or unsafe values as zero', () => {
  for (const seconds of [-0.001, Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY, Number.MAX_VALUE]) {
    expect(formatTimestamp(seconds)).toBe('00:00.000')
  }
})

test('formatted values can be pasted back without losing millisecond precision', () => {
  for (const seconds of [0, 0.001, 12.45, 59.999, 83.45, 3599.999, 3600, 3723.004, 86400.123]) {
    expect(parseTimestamp(formatTimestamp(seconds)), String(seconds)).toBe(seconds)
  }
})

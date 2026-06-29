// Polynomial approximation of matplotlib's perceptual "magma" colormap
// (public-domain fit by Matt Zucker / Anton Mikhailov). Avoids shipping a 768-value table.
function magma(t: number): [number, number, number] {
  const c0 = [-0.002136485053939, -0.000749655052795, -0.005386127855323]
  const c1 = [0.251820737525015, 0.677511465739363, 2.494026599323373]
  const c2 = [8.353717279216625, -3.577719514958484, 0.327827456607890]
  const c3 = [-27.66873308576866, 14.26473078096533, -13.64921318813922]
  const c4 = [52.17613981234068, -27.94360607168351, 12.94416944238394]
  const c5 = [-50.76852536473588, 29.04658282127291, 4.23415299384598]
  const c6 = [18.65570506591883, -11.48977351997711, -5.601961508734096]
  const ch = (i: number) =>
    c0[i] + t * (c1[i] + t * (c2[i] + t * (c3[i] + t * (c4[i] + t * (c5[i] + t * c6[i])))))
  return [ch(0), ch(1), ch(2)]
}

const toByte = (v: number) => Math.max(0, Math.min(255, Math.round(v * 255)))

/** 256-entry RGB lookup table (length 768): `lut[i*3 + {0,1,2}]` for intensity `i`. */
export function magmaLut(): Uint8Array {
  const lut = new Uint8Array(256 * 3)
  for (let i = 0; i < 256; i++) {
    const [r, g, b] = magma(i / 255)
    lut[i * 3] = toByte(r)
    lut[i * 3 + 1] = toByte(g)
    lut[i * 3 + 2] = toByte(b)
  }
  return lut
}

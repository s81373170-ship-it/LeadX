// Minimal ISO/IEC 18004 QR Code encoder (Byte mode, Error Correction Level M, Versions 1..10)

const GF_EXP = new Uint8Array(512);
const GF_LOG = new Uint8Array(256);
(() => {
  let x = 1;
  for (let i = 0; i < 255; i++) {
    GF_EXP[i] = x;
    GF_LOG[x] = i;
    x <<= 1;
    if (x & 0x100) x ^= 0x11d;
  }
  for (let i = 255; i < 512; i++) GF_EXP[i] = GF_EXP[i - 255];
})();

function gfMul(a, b) {
  if (a === 0 || b === 0) return 0;
  return GF_EXP[GF_LOG[a] + GF_LOG[b]];
}

function rsGeneratorPoly(degree) {
  let poly = [1];
  for (let i = 0; i < degree; i++) {
    const next = new Array(poly.length + 1).fill(0);
    const factor = GF_EXP[i];
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= poly[j];
      next[j + 1] ^= gfMul(poly[j], factor);
    }
    poly = next;
  }
  return poly;
}

function rsComputeEcc(data, ecCount) {
  const gen = rsGeneratorPoly(ecCount);
  const res = new Uint8Array(ecCount);
  for (let i = 0; i < data.length; i++) {
    const factor = data[i] ^ res[0];
    res.copyWithin(0, 1);
    res[ecCount - 1] = 0;
    for (let j = 0; j < ecCount; j++) {
      res[j] ^= gfMul(gen[j + 1], factor);
    }
  }
  return res;
}

// Level M capacity & RS block specs for Versions 1..10: [dataBytes, ecPerBlock, blocksG1, dataG1, blocksG2, dataG2]
const VERSION_M_SPECS = [
  null,
  [16, 10, 1, 16, 0, 0],
  [28, 16, 1, 28, 0, 0],
  [44, 26, 1, 44, 0, 0],
  [64, 18, 2, 32, 0, 0],
  [86, 24, 2, 43, 0, 0],
  [108, 16, 4, 27, 0, 0],
  [124, 18, 4, 31, 0, 0],
  [154, 22, 2, 38, 2, 39],
  [182, 22, 3, 36, 2, 37],
  [216, 26, 4, 43, 1, 44],
];

const ALIGNMENT_CENTERS = [
  [],
  [],
  [6, 18],
  [6, 22],
  [6, 26],
  [6, 30],
  [6, 34],
  [6, 22, 38],
  [6, 24, 42],
  [6, 26, 46],
  [6, 28, 50],
];

function computeFormatBits(mask) {
  // ECL M = 00 -> (00 << 3) | mask = mask
  const data = mask & 0x7;
  let rem = data << 10;
  for (let i = 14; i >= 10; i--) {
    if ((rem >> i) & 1) rem ^= 0x537 << (i - 10);
  }
  return ((data << 10) | rem) ^ 0x5412;
}

function computeVersionBits(version) {
  let rem = version << 12;
  for (let i = 17; i >= 12; i--) {
    if ((rem >> i) & 1) rem ^= 0x1f25 << (i - 12);
  }
  return (version << 12) | rem;
}

export function encodeQrMatrix(text) {
  const bytes = new TextEncoder().encode(String(text || ""));
  let version = 1;
  for (; version < VERSION_M_SPECS.length; version++) {
    const capacity = VERSION_M_SPECS[version][0];
    const headerBytes = version >= 10 ? 3 : 2;
    if (bytes.length + headerBytes <= capacity) break;
  }
  if (version >= VERSION_M_SPECS.length) {
    throw new Error("QR payload exceeds supported Version 10 capacity.");
  }

  const [totalDataBytes, ecPerBlock, g1Blocks, g1Data, g2Blocks, g2Data] = VERSION_M_SPECS[version];
  const bits = [];
  const pushBits = (val, count) => {
    for (let i = count - 1; i >= 0; i--) bits.push((val >> i) & 1);
  };

  pushBits(0b0100, 4); // Byte mode
  pushBits(bytes.length, version >= 10 ? 16 : 8);
  for (const b of bytes) pushBits(b, 8);
  const maxBits = totalDataBytes * 8;
  pushBits(0, Math.min(4, maxBits - bits.length));
  while (bits.length % 8 !== 0) bits.push(0);

  const dataBytes = [];
  for (let i = 0; i < bits.length; i += 8) {
    let v = 0;
    for (let j = 0; j < 8; j++) v = (v << 1) | bits[i + j];
    dataBytes.push(v);
  }
  for (let pad = 0; dataBytes.length < totalDataBytes; pad++) {
    dataBytes.push(pad % 2 === 0 ? 0xec : 0x11);
  }

  const blocks = [];
  const eccBlocks = [];
  let offset = 0;
  for (let b = 0; b < g1Blocks + g2Blocks; b++) {
    const len = b < g1Blocks ? g1Data : g2Data;
    const slice = dataBytes.slice(offset, offset + len);
    offset += len;
    blocks.push(slice);
    eccBlocks.push(rsComputeEcc(slice, ecPerBlock));
  }

  const interleaved = [];
  const maxDataLen = Math.max(g1Data, g2Data);
  for (let i = 0; i < maxDataLen; i++) {
    for (const blk of blocks) {
      if (i < blk.length) interleaved.push(blk[i]);
    }
  }
  for (let i = 0; i < ecPerBlock; i++) {
    for (const ecc of eccBlocks) interleaved.push(ecc[i]);
  }

  const size = version * 4 + 17;
  const grid = Array.from({ length: size }, () => new Uint8Array(size));
  const isFunction = Array.from({ length: size }, () => new Uint8Array(size));

  const setFn = (r, c, dark) => {
    if (r < 0 || c < 0 || r >= size || c >= size) return;
    grid[r][c] = dark ? 1 : 0;
    isFunction[r][c] = 1;
  };

  const drawFinder = (top, left) => {
    for (let dr = -1; dr <= 7; dr++) {
      for (let dc = -1; dc <= 7; dc++) {
        const r = top + dr;
        const c = left + dc;
        if (r < 0 || c < 0 || r >= size || c >= size) continue;
        const inOuter = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
        const border = dr === 0 || dr === 6 || dc === 0 || dc === 6;
        const inner = dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4;
        setFn(r, c, inOuter && (border || inner));
      }
    }
  };

  drawFinder(0, 0);
  drawFinder(0, size - 7);
  drawFinder(size - 7, 0);

  for (let i = 8; i < size - 8; i++) {
    setFn(6, i, i % 2 === 0);
    setFn(i, 6, i % 2 === 0);
  }

  const centers = ALIGNMENT_CENTERS[version];
  for (const cr of centers) {
    for (const cc of centers) {
      if ((cr <= 8 && cc <= 8) || (cr <= 8 && cc >= size - 9) || (cr >= size - 9 && cc <= 8)) {
        continue;
      }
      for (let dr = -2; dr <= 2; dr++) {
        for (let dc = -2; dc <= 2; dc++) {
          const dist = Math.max(Math.abs(dr), Math.abs(dc));
          setFn(cr + dr, cc + dc, dist === 2 || dist === 0);
        }
      }
    }
  }

  // Reserve format info + dark module
  for (let i = 0; i < 9; i++) {
    if (i !== 6) {
      setFn(8, i, 0);
      setFn(i, 8, 0);
    }
  }
  for (let i = 0; i < 8; i++) {
    setFn(8, size - 1 - i, 0);
    if (i < 7) setFn(size - 1 - i, 8, 0);
  }
  setFn(size - 8, 8, 1);

  if (version >= 7) {
    const vBits = computeVersionBits(version);
    for (let i = 0; i < 18; i++) {
      const bit = (vBits >> i) & 1;
      const a = Math.floor(i / 3);
      const b = size - 11 + (i % 3);
      setFn(a, b, bit);
      setFn(b, a, bit);
    }
  }

  // Place codeword bits
  const allBits = [];
  for (const byteVal of interleaved) {
    for (let i = 7; i >= 0; i--) allBits.push((byteVal >> i) & 1);
  }
  let bitIdx = 0;
  let upward = true;
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (let vert = 0; vert < size; vert++) {
      const r = upward ? size - 1 - vert : vert;
      for (let lr = 0; lr < 2; lr++) {
        const c = right - lr;
        if (!isFunction[r][c]) {
          grid[r][c] = bitIdx < allBits.length ? allBits[bitIdx++] : 0;
        }
      }
    }
    upward = !upward;
  }

  const applyMaskAndFormat = (mask) => {
    const m = grid.map((row) => new Uint8Array(row));
    for (let r = 0; r < size; r++) {
      for (let c = 0; c < size; c++) {
        if (isFunction[r][c]) continue;
        let invert = false;
        switch (mask) {
          case 0: invert = (r + c) % 2 === 0; break;
          case 1: invert = r % 2 === 0; break;
          case 2: invert = c % 3 === 0; break;
          case 3: invert = (r + c) % 3 === 0; break;
          case 4: invert = (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0; break;
          case 5: invert = ((r * c) % 2) + ((r * c) % 3) === 0; break;
          case 6: invert = (((r * c) % 2) + ((r * c) % 3)) % 2 === 0; break;
          case 7: invert = (((r + c) % 2) + ((r * c) % 3)) % 2 === 0; break;
        }
        if (invert) m[r][c] ^= 1;
      }
    }
    const fmt = computeFormatBits(mask);
    for (let i = 0; i < 15; i++) {
      const bit = (fmt >> i) & 1;
      if (i < 6) m[8][i] = bit;
      else if (i < 8) m[8][i + 1] = bit;
      else m[8][size - 15 + i] = bit;

      if (i < 8) m[size - 1 - i][8] = bit;
      else if (i < 9) m[15 - i][8] = bit;
      else m[14 - i][8] = bit;
    }
    m[size - 8][8] = 1;
    return m;
  };

  const scorePenalty = (m) => {
    let penalty = 0;
    for (let r = 0; r < size; r++) {
      let runColor = m[r][0];
      let runLen = 1;
      for (let c = 1; c < size; c++) {
        if (m[r][c] === runColor) {
          runLen++;
        } else {
          if (runLen >= 5) penalty += runLen - 2;
          runColor = m[r][c];
          runLen = 1;
        }
      }
      if (runLen >= 5) penalty += runLen - 2;
    }
    for (let c = 0; c < size; c++) {
      let runColor = m[0][c];
      let runLen = 1;
      for (let r = 1; r < size; r++) {
        if (m[r][c] === runColor) {
          runLen++;
        } else {
          if (runLen >= 5) penalty += runLen - 2;
          runColor = m[r][c];
          runLen = 1;
        }
      }
      if (runLen >= 5) penalty += runLen - 2;
    }
    for (let r = 0; r < size - 1; r++) {
      for (let c = 0; c < size - 1; c++) {
        const sum = m[r][c] + m[r + 1][c] + m[r][c + 1] + m[r + 1][c + 1];
        if (sum === 0 || sum === 4) penalty += 3;
      }
    }
    let darkCount = 0;
    for (let r = 0; r < size; r++) {
      for (let c = 0; c < size; c++) darkCount += m[r][c];
    }
    const pct = (darkCount * 100) / (size * size);
    penalty += Math.floor(Math.abs(pct - 50) / 5) * 10;
    return penalty;
  };

  let bestMatrix = null;
  let bestScore = Infinity;
  for (let mask = 0; mask < 8; mask++) {
    const candidate = applyMaskAndFormat(mask);
    const s = scorePenalty(candidate);
    if (s < bestScore) {
      bestScore = s;
      bestMatrix = candidate;
    }
  }

  return { size, version, matrix: bestMatrix };
}

export function renderQrSvg(text, quietZone = 2) {
  const { size, matrix } = encodeQrMatrix(text);
  const full = size + quietZone * 2;
  const rects = [];
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (matrix[r][c]) {
        rects.push(`<rect x="${c + quietZone}" y="${r + quietZone}" width="1" height="1" />`);
      }
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${full} ${full}" width="180" height="180" shape-rendering="crispEdges" role="img" aria-label="QR code to download LeadX Pro APK"><rect width="${full}" height="${full}" fill="#ffffff"/><g fill="#0a0a0a">${rects.join("")}</g></svg>`;
}
